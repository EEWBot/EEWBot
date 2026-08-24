package net.teamfruit.eewbot.entity.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordLimitsTest {

    private static final String GRINNING = "😀";           // U+1F600、1 コードポイント、UTF-16 では 2 単位
    private static final String FAMILY = "👨‍👩‍👧‍👦"; // 7 コードポイント

    @Test
    void countsCodePointsNotUtf16Units() {
        assertThat(DiscordLimits.codePoints("あ")).isEqualTo(1);
        assertThat(DiscordLimits.codePoints(GRINNING)).isEqualTo(1);
        assertThat(GRINNING.length()).isEqualTo(2);
    }

    @Test
    void countsZwjSequenceAsItsComponentCodePoints() {
        // Discord は書記素クラスタではなくコードポイントを数える: 家族絵文字は 7 消費する
        assertThat(DiscordLimits.codePoints(FAMILY)).isEqualTo(7);
    }

    @Test
    void countsUtf8Bytes() {
        assertThat(DiscordLimits.utf8Bytes("a")).isEqualTo(1);
        assertThat(DiscordLimits.utf8Bytes("あ")).isEqualTo(3);
        assertThat(DiscordLimits.utf8Bytes(GRINNING)).isEqualTo(4);
        assertThat(DiscordLimits.utf8Bytes(null)).isZero();
    }

    @Test
    void leavesShortTextAlone() {
        assertThat(DiscordLimits.truncate("hello", 10)).isEqualTo("hello");
        assertThat(DiscordLimits.truncate("hello", 5)).isEqualTo("hello");
        assertThat(DiscordLimits.truncate(null, 5)).isNull();
    }

    @Test
    void truncatesWithEllipsis() {
        assertThat(DiscordLimits.truncate("abcdefgh", 4)).isEqualTo("abc…");
        assertThat(DiscordLimits.codePoints(DiscordLimits.truncate("abcdefgh", 4))).isEqualTo(4);
    }

    @Test
    void neverSplitsASurrogatePair() {
        final String emoji = GRINNING.repeat(10);
        final String truncated = DiscordLimits.truncate(emoji, 4);

        assertThat(DiscordLimits.codePoints(truncated)).isEqualTo(4);
        assertThat(truncated).isEqualTo(GRINNING.repeat(3) + "…");
        // ペアが分断されると対のないサロゲートが残ってしまう
        for (int i = 0; i < truncated.length(); i++)
            assertThat(Character.isLowSurrogate(truncated.charAt(i)) && (i == 0 || !Character.isHighSurrogate(truncated.charAt(i - 1)))).isFalse();
    }
}
