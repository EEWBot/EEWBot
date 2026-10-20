package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;
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

    @Test
    void usesTheStricterWebhookBudgetEvenForBotMessageDelivery() {
        // 10,140 description bytes + red color fit the measured Message Create boundary
        // (10,151), but exceed the webhook boundary (10,136). Both paths share this packer.
        final PendingEmbed source = new PendingEmbed(null, "あ".repeat(3380), null, null, Color.RED,
                null, null, null, null, null, null, null, List.of());
        assertThat(EmbedWebhookCost.fits(List.of(source))).isFalse();
        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(source));
        assertThat(messages).hasSize(2);
        assertThat(messages.stream().flatMap(List::stream).map(PendingEmbed::description)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining()))
                .isEqualTo(source.description());
        messages.forEach(message -> assertThat(EmbedWebhookCost.fits(message)).isTrue());
    }
}
