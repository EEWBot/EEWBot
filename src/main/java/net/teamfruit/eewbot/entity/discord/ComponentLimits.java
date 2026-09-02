package net.teamfruit.eewbot.entity.discord;

import java.nio.charset.StandardCharsets;

public final class ComponentLimits {

    public static final int MAX_COMPONENTS_PER_MESSAGE = 40;
    public static final int MAX_TEXT_DISPLAY_CODE_POINTS_PER_MESSAGE = 4000;
    public static final int MAX_SECTION_CHILDREN = 3;
    public static final int MAX_MEDIA_GALLERY_ITEMS = 10;
    public static final int MAX_MEDIA_DESCRIPTION = 1024;
    public static final int IS_COMPONENTS_V2 = 1 << 15;

    private ComponentLimits() {
    }

    public static int codePoints(final String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    public static int utf8Bytes(final String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    public static String truncate(final String value, final int maxCodePoints) {
        if (value == null || codePoints(value) <= maxCodePoints)
            return value;
        if (maxCodePoints <= 0)
            return "";
        if (maxCodePoints == 1)
            return "…";
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints - 1)) + "…";
    }
}
