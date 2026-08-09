package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EmbedPacker {

    static final int MAX_TITLE_BYTES = 512;
    static final int MAX_DESCRIPTION_BYTES = 3100;
    static final int MAX_FIELD_NAME_BYTES = 512;
    static final int MAX_FOOTER_TEXT_BYTES = 1024;
    static final int MAX_AUTHOR_NAME_BYTES = 512;

    static final int FIELD_JSON_OVERHEAD = 40;
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
            final String name = truncateWarn(field.name(), DiscordLimits.MAX_FIELD_NAME, MAX_FIELD_NAME_BYTES, "field name");
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
                truncateWarn(embed.title(), DiscordLimits.MAX_TITLE, MAX_TITLE_BYTES, "title"),
                truncateWarn(embed.description(), DiscordLimits.MAX_DESCRIPTION, MAX_DESCRIPTION_BYTES, "description"),
                sanitizeUrl(embed.url(), "url"), embed.timestamp(), embed.color(),
                truncateWarn(embed.footerText(), DiscordLimits.MAX_FOOTER_TEXT, MAX_FOOTER_TEXT_BYTES, "footer text"),
                sanitizeUrl(embed.footerIcon(), "footer icon"),
                sanitizeUrl(embed.image(), "image"),
                sanitizeUrl(embed.thumbnail(), "thumbnail"),
                truncateWarn(embed.authorName(), DiscordLimits.MAX_AUTHOR_NAME, MAX_AUTHOR_NAME_BYTES, "author name"),
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

    private static String truncateWarn(final String s, final int maxCodePoints, final int maxBytes, final String what) {
        final String result = DiscordLimits.truncateBytes(DiscordLimits.truncate(s, maxCodePoints), maxBytes);
        if (!Objects.equals(s, result))
            Log.logger.warn("Truncating embed {} from {} code points ({} bytes) to {} code points ({} bytes)",
                    what, DiscordLimits.codePoints(s), DiscordLimits.jsonTextBytes(s),
                    DiscordLimits.codePoints(result), DiscordLimits.jsonTextBytes(result));
        return result;
    }

    private static String sanitizeUrl(final String url, final String what) {
        if (url == null || DiscordLimits.codePoints(url) <= DiscordLimits.MAX_URL)
            return url;
        Log.logger.warn("Dropping embed {} URL: {} code points exceeds the limit of {}",
                what, DiscordLimits.codePoints(url), DiscordLimits.MAX_URL);
        return null;
    }

    static List<PendingEmbed> paginate(final PendingEmbed embed) {
        final PendingEmbed head = embed.headChromeOnly().withFields(List.of());
        final PendingEmbed tail = embed.tailChromeOnly().withFields(List.of());
        final PendingEmbed plain = embed.colorChromeOnly().withFields(List.of());

        final List<List<PendingField>> pages = new ArrayList<>();
        List<PendingField> current = new ArrayList<>();
        int chars = head.charCount() + tail.charCount();
        int bytes = head.byteCount() + tail.byteCount() + MESSAGE_JSON_OVERHEAD;

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
                chars = head.charCount() + tail.charCount();
                bytes = head.byteCount() + tail.byteCount() + MESSAGE_JSON_OVERHEAD;
            }

            if (current.isEmpty()
                    && (chars + fieldChars > DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                    || bytes + fieldBytes > DiscordLimits.MAX_REQUEST_BYTES)) {
                field = fitToBudget(field, chars, bytes);
                fieldChars = fieldChars(field);
                fieldBytes = fieldBytes(field);
            }

            current.add(field);
            chars += fieldChars;
            bytes += fieldBytes;
        }
        pages.add(current);

        final List<PendingEmbed> result = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            final boolean isFirst = i == 0;
            final boolean isLast = i == pages.size() - 1;
            final PendingEmbed chrome;
            if (isFirst && isLast)
                chrome = embed;
            else if (isFirst)
                chrome = head;
            else if (isLast)
                chrome = tail;
            else
                chrome = plain;
            result.add(chrome.withFields(List.copyOf(pages.get(i))));
        }
        return result;
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
        final String value = DiscordLimits.truncateBytes(
                DiscordLimits.truncate(field.value(), Math.max(0, charBudget)), Math.max(0, byteBudget));
        Log.logger.warn("Truncating embed field value from {} code points ({} bytes) to {} code points ({} bytes): "
                        + "the embed's chrome leaves no room for a full field",
                DiscordLimits.codePoints(field.value()), DiscordLimits.jsonTextBytes(field.value()),
                DiscordLimits.codePoints(value), DiscordLimits.jsonTextBytes(value));
        return new PendingField(field.name(), value, field.inline());
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
                Log.logger.error("Embed page exceeds a whole message on its own: {} code points, {} bytes",
                        pageChars, pageBytes);

            current.add(page);
            chars += pageChars;
            bytes += pageBytes;
        }
        messages.add(List.copyOf(current));
        return messages;
    }
}
