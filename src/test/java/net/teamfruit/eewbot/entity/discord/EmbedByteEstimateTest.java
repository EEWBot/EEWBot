package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * パッカーはシリアライズ後サイズの見積もりに基づいて動く。見積もりが楽観的だと Discord は
 * 診断情報のない 500 を返すため、ここで実際の Gson 出力と突き合わせて検証する。
 */
class EmbedByteEstimateTest {

    private static String serialize(final List<PendingEmbed> message) {
        final DiscordWebhook webhook = DiscordWebhook.builder()
                .embeds(EmbedRenderer.toDiscordEmbeds(message))
                .build();
        webhook.avatar_url = "https://cdn.discordapp.com/avatars/000000000000000000/0123456789abcdef0123456789abcdef.png";
        webhook.username = "EEWBot";
        return Codecs.GSON.toJson(webhook);
    }

    private static PendingEmbed embedOf(final int fieldCount, final String value) {
        final List<PendingField> fields = new ArrayList<>();
        for (int i = 0; i < fieldCount; i++)
            fields.add(new PendingField("フィールド名" + i, value, false));
        return new PendingEmbed("タイトル", "説明文", null, Instant.EPOCH, Color.RED,
                "気象庁", null, "https://example.com/render/quake?id=1234567890", null,
                null, null, null, fields);
    }

    @Test
    void estimateIsNotOptimisticForJapaneseContent() {
        for (final List<PendingEmbed> message : EmbedPacker.pack(List.of(embedOf(120, "あ".repeat(200))))) {
            final int actual = serialize(message).getBytes(StandardCharsets.UTF_8).length;
            final int estimated = message.stream().mapToInt(PendingEmbed::byteCount).sum()
                    + EmbedPacker.MESSAGE_JSON_OVERHEAD;

            assertThat(actual)
                    .as("見積もりが実際のペイロードを下回ってはならない")
                    .isLessThanOrEqualTo(estimated);
            assertThat(actual).isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES);
        }
    }

    @Test
    void everyPackedMessageStaysUnderTheRealByteWall() {
        final List<PendingEmbed> many = new ArrayList<>();
        for (int i = 0; i < 40; i++)
            many.add(embedOf(8, "テスト地域名".repeat(30)));

        final List<List<PendingEmbed>> messages = EmbedPacker.pack(many);
        assertThat(messages).hasSizeGreaterThan(1);

        for (final List<PendingEmbed> message : messages)
            assertThat(serialize(message).getBytes(StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES);
    }

    @Test
    void longJapaneseDescriptionStaysUnderTheRealByteWall() {
        // description は固定のバイト上限ではなくメッセージの実サイズで抑えられる。
        // 境界を担保するのは定数ではなく、実際にシリアライズしたペイロード
        final PendingEmbed embed = new PendingEmbed("タイトル", "あ".repeat(4000), null,
                Instant.EPOCH, Color.RED, "気象庁", null, null, null, null, null, null,
                List.of(new PendingField("地域", "テスト地域名".repeat(50), false)));

        for (final List<PendingEmbed> message : EmbedPacker.pack(List.of(embed)))
            assertThat(serialize(message).getBytes(StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES);
    }

    @Test
    void escapeHeavyContentStillFits() {
        // 引用符・バックスラッシュ・改行は JSON エスケープでそれぞれサイズが倍になる
        final List<PendingEmbed> many = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            many.add(embedOf(6, "\"\\\n".repeat(200)));

        for (final List<PendingEmbed> message : EmbedPacker.pack(many))
            assertThat(serialize(message).getBytes(StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(DiscordLimits.MAX_REQUEST_BYTES);
    }
}
