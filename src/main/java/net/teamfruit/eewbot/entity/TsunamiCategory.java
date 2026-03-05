package net.teamfruit.eewbot.entity;

import discord4j.rest.util.Color;

public enum TsunamiCategory {
    NONE("00", 0, Color.of(127, 140, 141)),
    WARNING_CANCELLED("50", 0, Color.of(127, 140, 141)),
    WARNING("51", 3, Color.of(255, 40, 0)),
    MAJOR_WARNING("52", 4, Color.of(200, 0, 255)),
    MAJOR_WARNING_FIRING("53", 4, Color.of(200, 0, 255)),
    ADVISORY_CANCELLED("60", 0, Color.of(127, 140, 141)),
    ADVISORY("62", 2, Color.of(255, 40, 0)),
    FORECAST("71", 1, Color.of(0, 191, 255)),
    FORECAST_FROM_ADVISORY("72", 1, Color.of(0, 191, 255)),
    FORECAST_FROM_WARNING("73", 1, Color.of(0, 191, 255));

    private final String code;
    private final int priority;
    private final Color color;

    TsunamiCategory(String code, int priority, Color color) {
        this.code = code;
        this.priority = priority;
        this.color = color;
    }

    public static TsunamiCategory fromCode(String code) {
        for (TsunamiCategory v : values()) {
            if (v.code.equals(code)) return v;
        }
        return NONE;
    }

    public String getCode() {
        return this.code;
    }

    public int getPriority() {
        return this.priority;
    }

    public Color getColor() {
        return this.color;
    }
}
