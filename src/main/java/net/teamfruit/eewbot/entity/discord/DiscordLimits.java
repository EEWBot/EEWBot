package net.teamfruit.eewbot.entity.discord;

import java.nio.charset.StandardCharsets;

public final class DiscordLimits {

    public static final int MAX_EMBEDS_PER_MESSAGE = 10;
    public static final int MAX_FIELDS_PER_EMBED = 25;

    public static final int MAX_TITLE = 256;
    public static final int MAX_DESCRIPTION = 4096;
    public static final int MAX_FIELD_NAME = 256;
    public static final int MAX_FIELD_VALUE = 1024;
    public static final int MAX_FOOTER_TEXT = 2048;
    public static final int MAX_AUTHOR_NAME = 256;
    public static final int MAX_URL = 2048;

    /**
     * title、description、field name、field value、footer、author の合計
     * 上限はメッセージ内の全 embed を合算して適用される
     */
    public static final int MAX_TOTAL_CHARS_PER_MESSAGE = 6000;

    /**
     * リクエストボディ全体の UTF-8 サイズに対するマージン込みの上限
     * <p>
     * 10KB を超えると、文字数制限をすべて守っていても Discordは {@code HTTP 500} を返す
     */
    public static final int MAX_REQUEST_BYTES = 9000;

    private DiscordLimits() {
    }

    public static int codePoints(final String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    public static int utf8Bytes(final String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    public static int jsonTextBytes(final String s) {
        if (s == null)
            return 0;
        int total = utf8Bytes(s);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f')
                total += 1;
            else if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == '=' || c == '\'')
                total += 5;
        }
        return total;
    }

    public static String truncateBytes(final String s, final int maxBytes) {
        if (s == null || jsonTextBytes(s) <= maxBytes)
            return s;
        int count = codePoints(s);
        while (count > 0 && jsonTextBytes(truncate(s, count)) > maxBytes)
            count = Math.max(0, count - Math.max(1, (jsonTextBytes(truncate(s, count)) - maxBytes) / 4));
        return truncate(s, count);
    }

    public static String truncate(final String s, final int maxCodePoints) {
        if (s == null || codePoints(s) <= maxCodePoints)
            return s;
        if (maxCodePoints <= 0)
            return "";
        if (maxCodePoints == 1)
            return "…";
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints - 1)) + "…";
    }
}
