package net.teamfruit.eewbot.entity;

import discord4j.rest.util.Color;

@SuppressWarnings("NonAsciiCharacters")
public enum TsunamiCategory {
    津波なし("00", 0, Color.of(127, 140, 141)),
    /**
     * 大津波警報または津波警報の解除
     */
    警報解除("50", 0, Color.of(127, 140, 141)),
    津波警報("51", 3, Color.of(255, 40, 0)),
    大津波警報("52", 4, Color.of(200, 0, 255)),
    /**
     * 大津波警報の新規発表または切替
     */
    大津波警報_発表("53", 4, Color.of(200, 0, 255)),
    津波注意報解除("60", 0, Color.of(127, 140, 141)),
    津波注意報("62", 2, Color.of(255, 40, 0)),
    津波予報("71", 1, Color.of(0, 191, 255)),
    /**
     * 津波注意報解除、津波予報（若干の海面変動）への切替
     */
    津波予報_津波注意報解除("72", 1, Color.of(0, 191, 255)),
    /**
     * 大津波警報または津波警報の解除、津波予報（若干の海面変動）への切替
     */
    津波予報_大津波警報または津波警報解除("73", 1, Color.of(0, 191, 255));

    private final String code;
    private final int level;
    private final Color color;

    TsunamiCategory(String code, int level, Color color) {
        this.code = code;
        this.level = level;
        this.color = color;
    }

    public static TsunamiCategory fromCode(String code) {
        for (TsunamiCategory v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }

    public String getCode() {
        return this.code;
    }

    public int getLevel() {
        return this.level;
    }

    public Color getColor() {
        return this.color;
    }
}
