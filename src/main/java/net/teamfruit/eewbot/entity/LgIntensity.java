package net.teamfruit.eewbot.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LgIntensity {
    ZERO(0),
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4);

    private final int value;

    LgIntensity(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return this.value;
    }

    @JsonCreator
    public static LgIntensity get(int value) {
        for (final LgIntensity intensity : values()) {
            if (intensity.value == value) {
                return intensity;
            }
        }
        throw new IllegalArgumentException("No such lg intensity: " + value);
    }
}
