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

    private DiscordLimits() {
    }

    public static int codePoints(final String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    public static int utf8Bytes(final String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
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
