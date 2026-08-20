package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedPackerTest {

    private static PendingEmbed embed(final List<PendingField> fields) {
        return PendingEmbed.empty().withFields(fields);
    }

    private static List<PendingField> fields(final int count, final String value) {
        final List<PendingField> fields = new ArrayList<>();
        for (int i = 0; i < count; i++)
            fields.add(new PendingField("name" + i, value, false));
        return fields;
    }

    private static List<PendingEmbed> flatten(final List<List<PendingEmbed>> messages) {
        return messages.stream().flatMap(List::stream).toList();
    }

    @Test
    void keepsASmallEmbedIntact() {
        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(embed(fields(3, "value"))));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).hasSize(1);
        assertThat(messages.get(0).get(0).fields()).hasSize(3);
    }

    @Test
    void alwaysReturnsOneEmbedForEmptyInput() {
        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of());

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).hasSize(1);
    }

    @Test
    void splitsAtTwentyFiveFieldsPerEmbed() {
        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(embed(fields(26, "v")))));

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).fields()).hasSize(25);
        assertThat(pages.get(1).fields()).hasSize(1);
    }

    @Test
    void splitsOverlongFieldValuesIntoContinuationFields() {
        final String value = "x".repeat(2500);
        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(
                embed(List.of(new PendingField("area", value, false))))));

        final List<PendingField> all = pages.stream().flatMap(p -> p.fields().stream()).toList();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).name()).isEqualTo("area");
        // 継続フィールドは名前を持たず、見た目上ひとつのブロックとして読める
        assertThat(all.get(1).name()).isEmpty();
        assertThat(all.get(2).name()).isEmpty();
        all.forEach(f -> assertThat(DiscordLimits.codePoints(f.value())).isLessThanOrEqualTo(DiscordLimits.MAX_FIELD_VALUE));
    }

    @Test
    void prefersNewlineBoundariesWhenSplitting() {
        // 10 文字 x 300 行 = 3300 文字なので分割は必須だが、行の途中で切れてはならない
        final String value = String.join("\n", java.util.Collections.nCopies(300, "0123456789"));
        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(
                embed(List.of(new PendingField("area", value, false))))));

        pages.stream().flatMap(p -> p.fields().stream())
                .forEach(f -> f.value().lines().forEach(line -> assertThat(line).isEqualTo("0123456789")));
    }

    @Test
    void aggregatesTheSixThousandBudgetAcrossEmbedsOfAMessage() {
        // 3000 コードポイントの ASCII description 2 つは 1 メッセージに収まるが、3001 + 3000 は収まらない
        final PendingEmbed a = new PendingEmbed(null, "a".repeat(3000), null, null, null,
                null, null, null, null, null, null, null, List.of());
        final PendingEmbed b = new PendingEmbed(null, "b".repeat(3000), null, null, null,
                null, null, null, null, null, null, null, List.of());

        assertThat(EmbedPacker.group(List.of(a, b))).hasSize(1);

        final PendingEmbed tooLong = new PendingEmbed(null, "a".repeat(3001), null, null, null,
                null, null, null, null, null, null, null, List.of());
        assertThat(EmbedPacker.group(List.of(tooLong, b))).hasSize(2);
    }

    @Test
    void byteBudgetSplitsJapaneseBeforeTheCharacterBudgetIsReached() {
        // 日本語 165 コードポイント x 20 = 3300 文字(6000 未満)だが UTF-8 では約 9900 バイト(9000 超)
        final List<PendingField> japanese = fields(20, "あ".repeat(165));
        final int chars = japanese.stream()
                .mapToInt(f -> DiscordLimits.codePoints(f.name()) + DiscordLimits.codePoints(f.value())).sum();
        assertThat(chars).isLessThan(DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE);

        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(embed(japanese)));

        assertThat(messages).hasSizeGreaterThan(1);
        messages.forEach(message -> assertThat(message.stream().mapToInt(PendingEmbed::byteCount).sum())
                .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES));
    }

    @Test
    void splitsIntoMultipleMessagesBeyondTenEmbeds() {
        final List<PendingEmbed> embeds = new ArrayList<>();
        for (int i = 0; i < 11; i++)
            embeds.add(embed(List.of(new PendingField("n", "v", false))));

        final List<List<PendingEmbed>> messages = EmbedPacker.pack(embeds);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).hasSize(10);
        assertThat(messages.get(1)).hasSize(1);
    }

    @Test
    void truncatesTextThatCannotBeSplit() {
        final PendingEmbed normalized = EmbedPacker.normalize(new PendingEmbed(
                "t".repeat(300), null, null, null, null, null, null, null, null, null, null, null, List.of()));

        assertThat(DiscordLimits.codePoints(normalized.title())).isEqualTo(DiscordLimits.MAX_TITLE);
        assertThat(normalized.title()).endsWith("…");
    }

    @Test
    void keepsJapaneseDescriptionThatFitsTheRealBudget() {
        // あ×1500 は約 4500 バイト。文字数も合計文字数もリクエストサイズも全て上限内なので、
        // 送信可能な本文は 1 文字も欠けてはならない
        final String description = "あ".repeat(1500);
        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(new PendingEmbed(
                null, description, null, null, null, null, null, null, null, null, null, null, List.of()))));

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).description()).isEqualTo(description);
    }

    @Test
    void truncatesDescriptionOnlyWhenTheMessageBudgetIsExceeded() {
        // 日本語 4000 コードポイントは MAX_DESCRIPTION 未満だが約 12KB あり、500 が返る壁を越えている
        final String longJapanese = "あ".repeat(4000);
        assertThat(DiscordLimits.codePoints(longJapanese)).isLessThan(DiscordLimits.MAX_DESCRIPTION);

        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(new PendingEmbed(
                null, longJapanese, null, null, null, null, null, null, null, null, null, null, List.of()))));

        assertThat(pages).hasSize(1);
        final PendingEmbed only = pages.get(0);
        assertThat(only.description()).endsWith("…");
        assertThat(only.byteCount() + EmbedPacker.MESSAGE_JSON_OVERHEAD)
                .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES);
        // 固定上限ではなく実予算まで残るので、旧実装の約 1033 文字よりはるかに多く保たれる
        assertThat(DiscordLimits.codePoints(only.description())).isGreaterThan(2500);
    }

    @Test
    void leavesRoomForAFieldAlongsideAMaximalDescription() {
        // description を予算いっぱいに使うと、先頭ページにフィールドを載せられなくなる
        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(new PendingEmbed(
                "タイトル", "あ".repeat(4000), null, null, Color.RED, null, null, null, null,
                null, null, null, fields(3, "地域名".repeat(100)))));

        assertEveryLimitRespected(messages);
        final PendingEmbed first = flatten(messages).get(0);
        assertThat(first.description()).isNotEmpty();
        assertThat(first.fields()).isNotEmpty();
        first.fields().forEach(f -> assertThat(f.value()).isNotEmpty());
    }

    @Test
    void aSingleOversizedDescriptionStillProducesASendableMessage() {
        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(new PendingEmbed(
                "タイトル", "あ".repeat(4000), null, Instant.EPOCH, Color.RED, "気象庁",
                null, null, null, null, null, null, fields(30, "地域名".repeat(40)))));

        messages.forEach(message -> assertThat(message.stream().mapToInt(PendingEmbed::byteCount).sum())
                .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES));
    }

    @Test
    void keepsAllChromeOnASingleEmbedWhenNothingIsSplit() {
        final PendingEmbed source = new PendingEmbed("title", "desc", "https://example.com/a",
                Instant.EPOCH, Color.RED, "footer", null, "https://example.com/i.png", null,
                "author", null, null, List.of(new PendingField("n", "v", false)));

        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(source)));

        assertThat(pages).hasSize(1);
        final PendingEmbed only = pages.get(0);
        assertThat(only.title()).isEqualTo("title");
        assertThat(only.footerText()).isEqualTo("footer");
        assertThat(only.image()).isEqualTo("https://example.com/i.png");
        assertThat(only.timestamp()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void putsHeadChromeFirstAndTailChromeLast() {
        final PendingEmbed source = new PendingEmbed("title", "desc", null,
                Instant.EPOCH, Color.RED, "footer", null, "https://example.com/i.png", null,
                "author", null, null, fields(60, "v"));

        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(source)));
        assertThat(pages).hasSizeGreaterThan(2);

        final PendingEmbed first = pages.get(0);
        final PendingEmbed middle = pages.get(1);
        final PendingEmbed last = pages.get(pages.size() - 1);

        assertThat(first.title()).isEqualTo("title");
        assertThat(first.description()).isEqualTo("desc");
        assertThat(first.footerText()).isNull();
        assertThat(first.image()).isNull();
        assertThat(first.timestamp()).isNull();

        assertThat(middle.title()).isNull();
        assertThat(middle.footerText()).isNull();

        // Discord は画像・フッター・タイムスタンプを下部に描画するため、これらは末尾ページに載るべき
        assertThat(last.title()).isNull();
        assertThat(last.footerText()).isEqualTo("footer");
        assertThat(last.image()).isEqualTo("https://example.com/i.png");
        assertThat(last.authorName()).isEqualTo("author");
        assertThat(last.timestamp()).isEqualTo(Instant.EPOCH);

        // カラーバーは全ページに引き継がれ、embed 群がひとつのブロックとして読める
        pages.forEach(page -> assertThat(page.color()).isEqualTo(Color.RED));
    }

    private static void assertEveryLimitRespected(final List<List<PendingEmbed>> messages) {
        for (final List<PendingEmbed> message : messages) {
            assertThat(message).hasSizeLessThanOrEqualTo(DiscordLimits.MAX_EMBEDS_PER_MESSAGE);
            assertThat(message.stream().mapToInt(PendingEmbed::charCount).sum())
                    .isLessThanOrEqualTo(DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE);
            assertThat(message.stream().mapToInt(PendingEmbed::byteCount).sum()
                    + EmbedPacker.MESSAGE_JSON_OVERHEAD)
                    .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES);
            for (final PendingEmbed page : message) {
                assertThat(page.fields()).hasSizeLessThanOrEqualTo(DiscordLimits.MAX_FIELDS_PER_EMBED);
                page.fields().forEach(f -> assertThat(DiscordLimits.codePoints(f.value()))
                        .isLessThanOrEqualTo(DiscordLimits.MAX_FIELD_VALUE));
            }
        }
    }

    @Test
    void everyProducedMessageRespectsEveryLimit() {
        final PendingEmbed source = new PendingEmbed("title", "desc", null,
                Instant.EPOCH, Color.RED, "footer", null, null, null, "author", null, null,
                fields(120, "あ".repeat(200)));

        assertEveryLimitRespected(EmbedPacker.pack(List.of(source)));
    }

    @Test
    void dropsUrlsOverTheLimit() {
        // URL を切り詰めると不正な URL になるため、長すぎる URL は丸ごと除去するしかない
        final String tooLong = "https://example.com/" + "a".repeat(3000);
        final String fine = "https://example.com/i.png";

        final PendingEmbed normalized = EmbedPacker.normalize(new PendingEmbed(
                "title", "desc", tooLong, null, null, "footer", tooLong, tooLong, fine,
                "author", fine, tooLong, List.of()));

        assertThat(normalized.url()).isNull();
        assertThat(normalized.footerIcon()).isNull();
        assertThat(normalized.image()).isNull();
        assertThat(normalized.authorIcon()).isNull();

        // 制限内の URL とテキストプロパティはそのまま残る
        assertThat(normalized.thumbnail()).isEqualTo(fine);
        assertThat(normalized.authorUrl()).isEqualTo(fine);
        assertThat(normalized.title()).isEqualTo("title");
        assertThat(normalized.description()).isEqualTo("desc");
        assertThat(normalized.footerText()).isEqualTo("footer");
        assertThat(normalized.authorName()).isEqualTo("author");
    }

    @Test
    void doesNotChargeBothHeadAndTailChromeToEveryPage() {
        // head (title/description) と tail (footer/author) が同居するのは 1 ページに収まる場合だけ。
        // 分割されたページに両方を課金すると、実際には収まるフィールドまで切り詰めてしまう
        final String emoji = "😀".repeat(DiscordLimits.MAX_FIELD_VALUE);
        final PendingEmbed source = new PendingEmbed("t".repeat(256), "d".repeat(2900), null,
                Instant.EPOCH, Color.RED, "f".repeat(2048), null, null, null,
                "a".repeat(256), null, null, List.of(
                new PendingField("n1", emoji, false), new PendingField("n2", emoji, false)));

        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(source));

        assertEveryLimitRespected(messages);
        final List<PendingField> all = flatten(messages).stream().flatMap(p -> p.fields().stream()).toList();
        assertThat(all).hasSize(2);
        // 切り詰められていれば末尾が省略記号になり、コードポイント数も減る
        all.forEach(f -> {
            assertThat(f.value()).isEqualTo(emoji);
            assertThat(DiscordLimits.codePoints(f.value())).isEqualTo(DiscordLimits.MAX_FIELD_VALUE);
        });
    }

    @Test
    void everyPageFitsWithItsOwnChromeAfterRepacking() {
        // 3 ページ以上に分かれるケースで、末尾ページが tail chrome を載せた実サイズでも予算内に収まる
        final PendingEmbed source = new PendingEmbed("タイトル", "あ".repeat(1000), null,
                Instant.EPOCH, Color.RED, "気象庁".repeat(100), null, "https://example.com/i.png", null,
                "author", null, null, fields(40, "あ".repeat(300)));

        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(source));
        final List<PendingEmbed> pages = flatten(messages);
        assertThat(pages).hasSizeGreaterThan(2);

        assertEveryLimitRespected(messages);
        for (final PendingEmbed page : pages)
            assertThat(page.byteCount() + EmbedPacker.MESSAGE_JSON_OVERHEAD)
                    .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES);

        // 下部の装飾は末尾ページに載り、フィールドも道連れに削られていない
        final PendingEmbed last = pages.get(pages.size() - 1);
        assertThat(last.image()).isEqualTo("https://example.com/i.png");
        pages.stream().flatMap(p -> p.fields().stream())
                .forEach(f -> assertThat(f.value()).doesNotEndWith("…"));
    }

    @Test
    void chromeAloneNeverExceedsTheByteBudget() {
        // URL は個別に MAX_URL 以内でも、6 プロパティの合計では予算を超えられる。
        // 文字数には算入されないため 6000 の制限でも止まらず、フィールドがなければ
        // フィールド側の切り詰めも働かない
        final String url = "https://example.com/" + "あ".repeat(1980);
        assertThat(DiscordLimits.codePoints(url)).isLessThanOrEqualTo(DiscordLimits.MAX_URL);

        final PendingEmbed source = new PendingEmbed("t".repeat(256), "d".repeat(4000), url,
                Instant.EPOCH, Color.RED, "f".repeat(2048), url, url, url,
                "a".repeat(256), url, url, List.of());

        assertEveryLimitRespected(EmbedPacker.pack(List.of(source)));
    }

    @Test
    void dropsDecorativeUrlsBeforeTruncatingTheDescription() {
        // 装飾的なアイコンを落とすだけで収まるなら、気象情報本文も地震マップも守られる
        final String url = "https://example.com/" + "あ".repeat(900);
        final String description = "あ".repeat(1000);
        final PendingEmbed source = new PendingEmbed("タイトル", description, url,
                Instant.EPOCH, Color.RED, "気象庁", url, url, url,
                null, null, url, List.of());

        final List<PendingEmbed> pages = flatten(EmbedPacker.pack(List.of(source)));
        assertThat(pages).hasSize(1);
        final PendingEmbed only = pages.get(0);

        assertEveryLimitRespected(EmbedPacker.pack(List.of(source)));
        assertThat(only.description()).isEqualTo(description);
        assertThat(only.authorIcon()).isNull();
        assertThat(only.footerIcon()).isNull();
        assertThat(only.thumbnail()).isNull();
        // 地震マップとタイトルリンクは最後まで残る
        assertThat(only.image()).isEqualTo(url);
        assertThat(only.url()).isEqualTo(url);
    }

    @Test
    void escapeHeavyContentRespectsTheByteBudget() {
        // Gson がエスケープすると Discord タイムスタンプの山括弧は 1 つ 6 バイトになるため、
        // 生の UTF-8 サイズでフィールドを計測すると半分以上少なく見積もってしまう
        final String value = "<t:1700000000:f>".repeat(60);
        assertThat(DiscordLimits.jsonTextBytes(value)).isGreaterThan(DiscordLimits.utf8Bytes(value));

        assertEveryLimitRespected(EmbedPacker.pack(List.of(embed(fields(20, value)))));
    }

    @Test
    void maximalChromeAndLongUrlsStillProduceSendableMessages() {
        // 装飾要素と URL の合計で、フィールド 1 つ分にも満たない容量しか残らないことがある。
        // その場合フィールドは予算超過のまま出力されるのではなく、残り容量まで切り詰められる。
        // フィールドが 1 つだけなのが効いてくる: embed が 1 ページに収まり、そのページが
        // 上部・下部の装飾要素を同時に抱えることになるため。
        final String url = "https://example.com/" + "a".repeat(280);
        final PendingEmbed source = new PendingEmbed("t".repeat(256), "d".repeat(4000), url,
                Instant.EPOCH, Color.RED, "f".repeat(2048), url, url, url,
                "a".repeat(256), url, url, fields(1, "あ".repeat(1024)));

        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(source));

        assertEveryLimitRespected(messages);
        final List<PendingField> all = flatten(messages).stream().flatMap(p -> p.fields().stream()).toList();
        assertThat(all).isNotEmpty();
        // 値が空のフィールドは Discord に 400 で弾かれる
        all.forEach(f -> assertThat(f.value()).isNotEmpty());
    }
}
