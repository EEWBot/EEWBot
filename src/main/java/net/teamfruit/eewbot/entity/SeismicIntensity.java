package net.teamfruit.eewbot.entity;

import discord4j.rest.util.Color;

public enum SeismicIntensity {
    UNKNOWN("不明", "不明", "unknown", Color.of(127, 140, 141)),
    ONE("1", "1", "1", Color.of(127, 140, 141)),
    TWO("2", "2", "2", Color.of(41, 128, 185)),
    THREE("3", "3", "3", Color.of(39, 174, 96)),
    FOUR("4", "4", "4", Color.of(221, 176, 0)),
    FIVE_MINUS("5弱", "5-", "5-", Color.of(230, 126, 34)),
    FIVE_PLUS("5強", "5+", "5+", Color.of(182, 72, 18)),
    SIX_MINUS("6弱", "6-", "6-", Color.of(255, 118, 188)),
    SIX_PLUS("6強", "6+", "6+", Color.of(255, 46, 18)),
    SEVEN("7", "7", "7", Color.of(114, 0, 172));

    private final String name;
    private final String symbol;
    private final String legacySerializedName;
    private final Color color;

    SeismicIntensity(final String name, final String symbol, final String legacySerializedName, final Color color) {
        this.name = name;
        this.symbol = symbol;
        this.legacySerializedName = legacySerializedName;
        this.color = color;
    }

    public String getSimple() {
        return this.name;
    }

    public String getSymbolIntensity() {
        return this.symbol;
    }

    public Color getColor() {
        return this.color;
    }

    public String getLegacySerializedName() {
        return this.legacySerializedName;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static SeismicIntensity get(final String name) {
        for (final SeismicIntensity value : values()) {
            if (value.name.equals(name) || value.symbol.equals(name)) {
                return value;
            }
        }
        return UNKNOWN;
    }

}
