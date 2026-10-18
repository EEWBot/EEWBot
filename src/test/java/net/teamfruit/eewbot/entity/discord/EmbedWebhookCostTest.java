package net.teamfruit.eewbot.entity.discord;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedWebhookCostTest {

    @Test
    void rejectsTheMeasuredSingleDescriptionFailure() {
        // 10,143 succeeds and 10,144 fails on the measured webhook, without an explicit color.
        final PendingEmbed failure = PendingEmbed.empty().withDescription("あ".repeat(3381) + "a");
        assertThat(EmbedWebhookCost.fits(List.of(failure))).isFalse();
    }

    @Test
    void leavesRoomForTheMeasuredWebhookNameAndAvatarVariation() {
        final PendingEmbed source = PendingEmbed.empty().withDescription("あ".repeat(4000));
        final PendingEmbed first = EmbedPacker.pack(List.of(source)).getFirst().getFirst();
        final int bytes = DiscordLimits.utf8Bytes(first.description());
        // 80 emoji in the username + resolved avatar: 9,794 succeeds, 9,795 fails (no color).
        assertThat(bytes).isLessThan(9794);
        assertThat(bytes).isGreaterThan(9500);
    }

    @Test
    void rendererAndSendTimeValidationUseTheSameAccounting() {
        final List<PendingEmbed> message = List.of(PendingEmbed.empty().withDescription("あ<&\n😀".repeat(100)));
        final DiscordWebhook webhook = DiscordWebhook.builder().embeds(EmbedRenderer.toDiscordEmbeds(message))
                .username("EEWBot").avatar_url("https://example.com/avatar.png").build();
        assertThat(EmbedWebhookCost.estimate(webhook)).isEqualTo(EmbedWebhookCost.estimate(message));
    }
}
