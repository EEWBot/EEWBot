package net.teamfruit.eewbot.entity.discord;

import java.nio.charset.StandardCharsets;

public final class ComponentLimits {

    public static final int MAX_COMPONENTS_PER_MESSAGE = 40;
    public static final int MAX_TEXT_DISPLAY_CODE_POINTS_PER_MESSAGE = 4000;
    public static final int MAX_SECTION_CHILDREN = 3;
    public static final int MAX_MEDIA_GALLERY_ITEMS = 10;
    public static final int MAX_MEDIA_DESCRIPTION = 1024;
    /**
     * Conservative budget below Discord's observed request-processing failure boundary.
     */
    public static final int MAX_REQUEST_BYTES = 9000;
    /**
     * Leaves room for webhook-level fields such as avatar_url that are added after entity rendering.
     */
    public static final int REQUEST_METADATA_RESERVE_BYTES = 2200;
    public static final int MAX_PACKED_COMPONENT_BODY_BYTES = MAX_REQUEST_BYTES - REQUEST_METADATA_RESERVE_BYTES;
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
