package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EmbedPacker {

    static final int FIELD_JSON_OVERHEAD = 40;
    /**
     * {@code "fields":[],} フィールドを持つページだけが払う
     */
    static final int FIELDS_ARRAY_JSON_OVERHEAD = 12;
    static final int EMBED_JSON_OVERHEAD = 8;
    static final int MESSAGE_JSON_OVERHEAD = 256;

    private EmbedPacker() {
    }

    public static List<List<PendingEmbed>> pack(final List<PendingEmbed> embeds) {
        final List<PendingEmbed> pages = new ArrayList<>();
        for (final PendingEmbed embed : embeds)
            pages.addAll(paginate(normalize(embed)));

        if (pages.isEmpty())
            pages.add(PendingEmbed.empty());

        return group(pages);
    }

    static PendingEmbed normalize(final PendingEmbed embed) {
        final List<PendingField> fields = new ArrayList<>();
        for (final PendingField field : embed.fields()) {
            final String name = truncateWarn(field.name(), DiscordLimits.MAX_FIELD_NAME, "field name");
            if (DiscordLimits.codePoints(field.value()) <= DiscordLimits.MAX_FIELD_VALUE) {
                fields.add(new PendingField(name, field.value(), field.inline()));
                continue;
            }
            boolean first = true;
            for (final String chunk : splitValue(field.value())) {
                fields.add(new PendingField(first ? name : "", chunk, field.inline()));
                first = false;
            }
        }

        return new PendingEmbed(
                truncateWarn(embed.title(), DiscordLimits.MAX_TITLE, "title"),
                truncateWarn(embed.description(), DiscordLimits.MAX_DESCRIPTION, "description"),
                sanitizeUrl(embed.url(), "url"), embed.timestamp(), embed.color(),
                truncateWarn(embed.footerText(), DiscordLimits.MAX_FOOTER_TEXT, "footer text"),
                sanitizeUrl(embed.footerIcon(), "footer icon"),
                sanitizeUrl(embed.image(), "image"),
                sanitizeUrl(embed.thumbnail(), "thumbnail"),
                truncateWarn(embed.authorName(), DiscordLimits.MAX_AUTHOR_NAME, "author name"),
                sanitizeUrl(embed.authorUrl(), "author url"),
                sanitizeUrl(embed.authorIcon(), "author icon"), fields);
    }

    static List<String> splitValue(final String value) {
        final List<String> chunks = new ArrayList<>();
        String remaining = value;
        while (DiscordLimits.codePoints(remaining) > DiscordLimits.MAX_FIELD_VALUE) {
            int cut = remaining.offsetByCodePoints(0, DiscordLimits.MAX_FIELD_VALUE);
            final int newline = remaining.lastIndexOf('\n', cut - 1);
            if (newline > 0)
                cut = newline + 1;
            chunks.add(stripTrailingNewline(remaining.substring(0, cut)));
            remaining = remaining.substring(cut);
        }
        if (!remaining.isEmpty())
            chunks.add(remaining);
        return chunks;
    }

    private static String stripTrailingNewline(final String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncateWarn(final String s, final int maxCodePoints, final String what) {
        final String result = DiscordLimits.truncate(s, maxCodePoints);
        if (!Objects.equals(s, result))
            Log.logger.warn("Truncating embed {} from {} code points to {}: Discord's limit for this property",
                    what, DiscordLimits.codePoints(s), maxCodePoints);
        return result;
    }

    private static String sanitizeUrl(final String url, final String what) {
        if (url == null || DiscordLimits.codePoints(url) <= DiscordLimits.MAX_URL)
            return url;
        Log.logger.warn("Dropping embed {} URL: {} code points exceeds the limit of {}",
                what, DiscordLimits.codePoints(url), DiscordLimits.MAX_URL);
        return null;
    }

    private static final int TITLE = 0, DESCRIPTION = 1, URL = 2, FOOTER_TEXT = 3, FOOTER_ICON = 4,
            IMAGE = 5, THUMBNAIL = 6, AUTHOR_NAME = 7, AUTHOR_URL = 8, AUTHOR_ICON = 9;

    private static final String[] CHROME_NAMES = {"title", "description", "url", "footer text",
            "footer icon", "image", "thumbnail", "author name", "author url", "author icon"};

    /**
     * 装飾的で、失っても情報が欠けない URL
     */
    private static final int[] DECORATIVE_URLS = {AUTHOR_ICON, FOOTER_ICON, THUMBNAIL};
    /**
     * 本文を切り詰めてもなお収まらない場合にだけ捨てる URL
     */
    private static final int[] ESSENTIAL_URLS = {AUTHOR_URL, URL, IMAGE};
    /**
     * 最後の手段としてバイト予算まで切り詰めるテキスト
     */
    private static final int[] TEXT_LAST_RESORT = {FOOTER_TEXT, AUTHOR_NAME, TITLE};

    /**
     * chrome のうち、ひとつのページに同時に載る範囲
     * <p>
     * 分割されると先頭ページは {@link PendingEmbed#headChromeOnly()} しか、末尾ページは
     * {@link PendingEmbed#tailChromeOnly()} しか持たない。1 ページに収まるときだけ両者が同居する
     */
    enum ChromeScope {
        HEAD(Set.of(TITLE, DESCRIPTION, URL, THUMBNAIL)),
        TAIL(Set.of(FOOTER_TEXT, FOOTER_ICON, IMAGE, AUTHOR_NAME, AUTHOR_URL, AUTHOR_ICON)),
        FULL(null);

        private final Set<Integer> slots;

        ChromeScope(final Set<Integer> slots) {
            this.slots = slots;
        }

        boolean covers(final int slot) {
            return this.slots == null || this.slots.contains(slot);
        }

        int bytes(final PendingEmbed embed) {
            int total = MESSAGE_JSON_OVERHEAD;
            if (this != TAIL)
                total += embed.headChromeOnly().withFields(List.of()).byteCount();
            if (this != HEAD)
                total += embed.tailChromeOnly().withFields(List.of()).byteCount();
            // chrome がページを食い潰してフィールドを 1 つも載せられない状態を避ける
            if (!embed.fields().isEmpty())
                total += FIELDS_ARRAY_JSON_OVERHEAD + fieldBytes(embed.fields().get(0));
            return total;
        }

        int chars(final PendingEmbed embed) {
            int total = 0;
            if (this != TAIL)
                total += embed.headChromeOnly().withFields(List.of()).charCount();
            if (this != HEAD)
                total += embed.tailChromeOnly().withFields(List.of()).charCount();
            if (!embed.fields().isEmpty())
                total += fieldChars(embed.fields().get(0));
            return total;
        }
    }

    /**
     * スコープが載せる chrome をメッセージの実予算に合わせる
     * <p>
     * Discord の制限は文字数ベースだが、リクエストボディ全体が {@link DiscordLimits#MAX_REQUEST_BYTES}
     * を超えると 500 が返る。プロパティごとに固定のバイト上限を置くと送信可能な本文まで削って
     * しまうため、実際の chrome と実際の先頭フィールドから残り予算を求め、本当に溢れる分だけ削る
     * <p>
     * URL は個別に {@link DiscordLimits#MAX_URL} 以内でも、6 つのプロパティの合計では予算を
     * 超えられる ({@link PendingEmbed#charCount()} にも算入されないため文字数制限では止まらない)。
     * そのため失って影響の小さいものから順に落とし、戻り値が必ず予算内に収まることを保証する。
     * 全段階を経れば color と timestamp しか残らないので、収束は保証される
     * <p>
     * スコープ外のプロパティは同じページに載らず 1 バイトも予算を消費しないため、対象にしない
     */
    static PendingEmbed fitChrome(final PendingEmbed embed, final ChromeScope scope) {
        final String[] chrome = {embed.title(), embed.description(), embed.url(), embed.footerText(),
                embed.footerIcon(), embed.image(), embed.thumbnail(), embed.authorName(),
                embed.authorUrl(), embed.authorIcon()};

        for (final int slot : DECORATIVE_URLS)
            dropUrl(embed, chrome, slot, scope);
        if (scope.covers(DESCRIPTION))
            fitDescription(embed, chrome, scope);
        for (final int slot : ESSENTIAL_URLS)
            dropUrl(embed, chrome, slot, scope);
        for (final int slot : TEXT_LAST_RESORT)
            trimText(embed, chrome, slot, scope);

        return rebuild(embed, chrome);
    }

    private static boolean fits(final PendingEmbed embed, final String[] chrome, final ChromeScope scope) {
        return scope.bytes(rebuild(embed, chrome)) <= DiscordLimits.MAX_REQUEST_BYTES;
    }

    /**
     * 切り詰めると不正な URL になるため、予算のために手放すときは丸ごと落とす
     */
    private static void dropUrl(final PendingEmbed embed, final String[] chrome, final int slot,
                                final ChromeScope scope) {
        if (!scope.covers(slot) || chrome[slot] == null || fits(embed, chrome, scope))
            return;
        Log.logger.warn("Dropping embed {} URL ({} bytes): the page does not fit in {} bytes with it",
                CHROME_NAMES[slot], DiscordLimits.jsonTextBytes(chrome[slot]), DiscordLimits.MAX_REQUEST_BYTES);
        chrome[slot] = null;
    }

    private static void fitDescription(final PendingEmbed embed, final String[] chrome,
                                       final ChromeScope scope) {
        final String description = chrome[DESCRIPTION];
        if (description == null)
            return;

        // 空文字にすることで description のテキスト分だけを予算から外す。JSON のキー分の
        // オーバーヘッドは byteCount() 側の定義に任せたまま残る
        chrome[DESCRIPTION] = "";
        final PendingEmbed bare = rebuild(embed, chrome);

        final int byteBudget = Math.max(0, DiscordLimits.MAX_REQUEST_BYTES - scope.bytes(bare));
        final int charBudget = Math.max(0, DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE - scope.chars(bare));

        final String fitted = DiscordLimits.truncateBytes(
                DiscordLimits.truncate(description, charBudget), byteBudget);
        chrome[DESCRIPTION] = fitted.isEmpty() ? null : fitted;

        if (!Objects.equals(description, fitted))
            Log.logger.warn("Truncating embed description from {} code points ({} bytes) to {} code points "
                            + "({} bytes): the rest of the page leaves only {} code points / {} bytes",
                    DiscordLimits.codePoints(description), DiscordLimits.jsonTextBytes(description),
                    DiscordLimits.codePoints(fitted), DiscordLimits.jsonTextBytes(fitted), charBudget, byteBudget);
    }

    private static void trimText(final PendingEmbed embed, final String[] chrome, final int slot,
                                 final ChromeScope scope) {
        if (!scope.covers(slot) || chrome[slot] == null || fits(embed, chrome, scope))
            return;

        final String original = chrome[slot];
        chrome[slot] = "";
        final int byteBudget = Math.max(0, DiscordLimits.MAX_REQUEST_BYTES - scope.bytes(rebuild(embed, chrome)));
        final String fitted = DiscordLimits.truncateBytes(original, byteBudget);
        chrome[slot] = fitted.isEmpty() ? null : fitted;

        Log.logger.warn("Truncating embed {} from {} bytes to {} bytes: the rest of the page leaves "
                        + "only {} bytes", CHROME_NAMES[slot], DiscordLimits.jsonTextBytes(original),
                DiscordLimits.jsonTextBytes(fitted), byteBudget);
    }

    private static PendingEmbed rebuild(final PendingEmbed embed, final String[] chrome) {
        return new PendingEmbed(chrome[TITLE], chrome[DESCRIPTION], chrome[URL], embed.timestamp(),
                embed.color(), chrome[FOOTER_TEXT], chrome[FOOTER_ICON], chrome[IMAGE], chrome[THUMBNAIL],
                chrome[AUTHOR_NAME], chrome[AUTHOR_URL], chrome[AUTHOR_ICON], embed.fields());
    }

    static List<PendingEmbed> paginate(final PendingEmbed source) {
        // 分割される場合、先頭ページは head しか、末尾ページは tail しか載せない。
        // この時点では両者が同居する前提を置かず、それぞれが単独で予算に収まることだけを保証する
        PendingEmbed embed = fitChrome(fitChrome(source, ChromeScope.HEAD), ChromeScope.TAIL);

        // 第 1 段階: 先頭ページは head、以降は color だけを課金してフィールドを分配する。
        // tail がどのページに載るかは分割が終わるまで決まらないので、ここでは課金しない
        final List<List<PendingField>> pages = distribute(embed,
                embed.headChromeOnly().withFields(List.of()), embed.colorChromeOnly().withFields(List.of()));

        // 1 ページに収まった場合だけ head と tail が同居する。ここで初めて両者を同時に課金して
        // 調整する。切り詰めはページを軽くする方向にしか働かないので、分配のやり直しは要らない
        if (pages.size() == 1)
            embed = fitChrome(embed, ChromeScope.FULL);

        final PendingEmbed full = embed.withFields(List.of());
        final PendingEmbed head = embed.headChromeOnly().withFields(List.of());
        final PendingEmbed tail = embed.tailChromeOnly().withFields(List.of());
        final PendingEmbed plain = embed.colorChromeOnly().withFields(List.of());

        // 第 2 段階: 末尾ページに tail を載せ、それで溢れる分だけ後ろへ押し出す
        spillForTail(pages, full, head, tail, plain);

        final List<PendingEmbed> result = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++)
            result.add(chromeFor(i, pages.size(), full, head, tail, plain)
                    .withFields(List.copyOf(pages.get(i))));
        return result;
    }

    /**
     * 先頭ページに head、以降のページに color だけを課金してフィールドを分配する
     */
    private static List<List<PendingField>> distribute(final PendingEmbed embed,
                                                       final PendingEmbed head, final PendingEmbed plain) {
        final List<List<PendingField>> pages = new ArrayList<>();
        List<PendingField> current = new ArrayList<>();
        int chars = head.charCount();
        int bytes = head.byteCount() + MESSAGE_JSON_OVERHEAD + FIELDS_ARRAY_JSON_OVERHEAD;

        for (final PendingField original : embed.fields()) {
            PendingField field = original;
            int fieldChars = fieldChars(field);
            int fieldBytes = fieldBytes(field);

            final boolean overflows = current.size() >= DiscordLimits.MAX_FIELDS_PER_EMBED
                    || chars + fieldChars > DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                    || bytes + fieldBytes > DiscordLimits.MAX_REQUEST_BYTES;

            if (overflows && !current.isEmpty()) {
                pages.add(current);
                current = new ArrayList<>();
                chars = plain.charCount();
                bytes = plain.byteCount() + MESSAGE_JSON_OVERHEAD + FIELDS_ARRAY_JSON_OVERHEAD;
            }

            if (current.isEmpty()
                    && (chars + fieldChars > DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                    || bytes + fieldBytes > DiscordLimits.MAX_REQUEST_BYTES)) {
                field = fitToBudget(field, chars, bytes);
                warnTruncated(original, field);
                fieldChars = fieldChars(field);
                fieldBytes = fieldBytes(field);
            }

            current.add(field);
            chars += fieldChars;
            bytes += fieldBytes;
        }
        pages.add(current);
        return pages;
    }

    /**
     * 末尾ページに tail chrome を載せ、収まらない分のフィールドを後ろのページへ押し出す
     * <p>
     * ページを増やすと直前のページは tail を手放して軽くなるだけなので、押し出しは必ず終わる。
     * フィールドがひとつしか残っていないページは押し出す先がないため、残予算まで切り詰める
     */
    private static void spillForTail(final List<List<PendingField>> pages, final PendingEmbed full,
                                     final PendingEmbed head, final PendingEmbed tail,
                                     final PendingEmbed plain) {
        while (true) {
            final int index = pages.size() - 1;
            final PendingEmbed chrome = chromeFor(index, pages.size(), full, head, tail, plain);
            final List<PendingField> fields = pages.get(index);
            if (!overflows(chrome.withFields(List.copyOf(fields))))
                return;

            if (fields.size() < 2) {
                // chrome だけで超過。fitChrome() の保証が破れていない限り到達しない
                if (fields.isEmpty())
                    return;

                final PendingField only = fields.get(0);
                final PendingField fitted = fitToBudget(only, chrome.charCount(),
                        chrome.byteCount() + MESSAGE_JSON_OVERHEAD + FIELDS_ARRAY_JSON_OVERHEAD);
                if (!overflows(chrome.withFields(List.of(fitted)))) {
                    warnTruncated(only, fitted);
                    fields.set(0, fitted);
                    return;
                }

                // fitToBudget() が縮められるのは value だけなので、name が重いと切り詰めても
                // 収まらない。フィールドは無傷のまま直前ページに残し、tail だけのページを足す。
                // 元の末尾ページは color だけの chrome になって必ず軽くなる
                Log.logger.warn("Moving the tail chrome onto a page of its own: the last page cannot hold it "
                        + "together with a field named {} bytes long", DiscordLimits.jsonTextBytes(only.name()));
                pages.add(new ArrayList<>());
                return;
            }
            pages.add(new ArrayList<>(List.of(fields.remove(fields.size() - 1))));
        }
    }

    private static boolean overflows(final PendingEmbed page) {
        return page.charCount() > DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                || page.byteCount() + MESSAGE_JSON_OVERHEAD > DiscordLimits.MAX_REQUEST_BYTES;
    }

    /**
     * ページ index が実際に載せる chrome
     * <p>
     * 1 ページに収まるなら head と tail は同居する。分割される場合は Discord の描画順に合わせ、
     * 先頭ページが上部の装飾を、末尾ページが下部の装飾を持つ
     */
    private static PendingEmbed chromeFor(final int index, final int pageCount, final PendingEmbed full,
                                          final PendingEmbed head, final PendingEmbed tail,
                                          final PendingEmbed plain) {
        if (pageCount <= 1)
            return full;
        if (index == 0)
            return head;
        return index == pageCount - 1 ? tail : plain;
    }

    private static int fieldChars(final PendingField field) {
        return DiscordLimits.codePoints(field.name()) + DiscordLimits.codePoints(field.value());
    }

    private static int fieldBytes(final PendingField field) {
        return FIELD_JSON_OVERHEAD
                + DiscordLimits.jsonTextBytes(field.name()) + DiscordLimits.jsonTextBytes(field.value());
    }

    private static PendingField fitToBudget(final PendingField field, final int chars, final int bytes) {
        final int charBudget = DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE - chars - DiscordLimits.codePoints(field.name());
        final int byteBudget = DiscordLimits.MAX_REQUEST_BYTES - bytes - FIELD_JSON_OVERHEAD
                - DiscordLimits.jsonTextBytes(field.name());
        final String truncated = DiscordLimits.truncateBytes(
                DiscordLimits.truncate(field.value(), Math.max(0, charBudget)), Math.max(0, byteBudget));
        // 値が空のフィールドは Discord に 400 で弾かれるため、最低でも省略記号は残す
        return new PendingField(field.name(), truncated.isEmpty() ? "…" : truncated, field.inline());
    }

    /**
     * 切り詰めを採用したときだけ呼ぶ。結果を破棄する経路があるため {@link #fitToBudget} 自体は記録しない
     */
    private static void warnTruncated(final PendingField original, final PendingField fitted) {
        Log.logger.warn("Truncating embed field value from {} code points ({} bytes) to {} code points ({} bytes): "
                        + "the embed's chrome leaves no room for a full field",
                DiscordLimits.codePoints(original.value()), DiscordLimits.jsonTextBytes(original.value()),
                DiscordLimits.codePoints(fitted.value()), DiscordLimits.jsonTextBytes(fitted.value()));
    }

    static List<List<PendingEmbed>> group(final List<PendingEmbed> pages) {
        final List<List<PendingEmbed>> messages = new ArrayList<>();
        List<PendingEmbed> current = new ArrayList<>();
        int chars = 0;
        int bytes = MESSAGE_JSON_OVERHEAD;

        for (final PendingEmbed page : pages) {
            final int pageChars = page.charCount();
            final int pageBytes = page.byteCount();

            final boolean overflows = current.size() >= DiscordLimits.MAX_EMBEDS_PER_MESSAGE
                    || chars + pageChars > DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                    || bytes + pageBytes > DiscordLimits.MAX_REQUEST_BYTES;

            if (overflows && !current.isEmpty()) {
                messages.add(List.copyOf(current));
                current = new ArrayList<>();
                chars = 0;
                bytes = MESSAGE_JSON_OVERHEAD;
            }

            if (current.isEmpty()
                    && (pageChars > DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                    || bytes + pageBytes > DiscordLimits.MAX_REQUEST_BYTES))
                // fitChrome() が chrome を、distribute()/spillForTail() がフィールドを予算内に
                // 収めているため、ここには到達しない。到達したら実装バグ
                Log.logger.error("Embed page exceeds a whole message on its own ({} code points, {} bytes): "
                        + "the packer failed to keep it within budget", pageChars, pageBytes);

            current.add(page);
            chars += pageChars;
            bytes += pageBytes;
        }
        messages.add(List.copyOf(current));
        return messages;
    }
}
