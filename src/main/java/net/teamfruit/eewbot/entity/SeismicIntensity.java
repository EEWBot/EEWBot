package net.teamfruit.eewbot.entity;

import com.google.gson.annotations.SerializedName;
import discord4j.rest.util.Color;

import java.util.Optional;
import java.util.stream.Stream;

public enum SeismicIntensity {
    @SerializedName("unknown")
    UNKNOWN("不明", "不明", Color.of(127, 140, 141)),
    @SerializedName("1")
    ONE("1", "1", Color.of(127, 140, 141)),
    @SerializedName("2")
    TWO("2", "2", Color.of(41, 128, 185)),
    @SerializedName("3")
    THREE("3", "3", Color.of(39, 174, 96)),
    @SerializedName("4")
    FOUR("4", "4", Color.of(221, 176, 0)),
    @SerializedName("5-")
    FIVE_MINUS("5弱", "5-", Color.of(230, 126, 34)),
    @SerializedName("5+")
    FIVE_PLUS("5強", "5+", Color.of(182, 72, 18)),
    @SerializedName("6-")
    SIX_MINUS("6弱", "6-", Color.of(255, 118, 188)),
    @SerializedName("6+")
    SIX_PLUS("6強", "6+", Color.of(255, 46, 18)),
    @SerializedName("7")
    SEVEN("7", "7", Color.of(114, 0, 172));

    private final String name;
    private final String symbol;
    private final Color color;

    SeismicIntensity(final String name, final String symbol, final Color color) {
        this.name = name;
        this.symbol = symbol;
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

    @Override
    public String toString() {
        return this.name;
    }

    public static Optional<SeismicIntensity> get(final String name) {
        return Stream.of(values()).filter(value -> value.getSimple().equals(name) || value.getSymbolIntensity().equals(name)).findAny();
    }

}
