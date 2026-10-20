package net.teamfruit.eewbot.entity.discord;

import java.util.List;

/**
 * Shared Message Create / Execute Webhook admission budget. Calibrated on webhooks
 * on 2026-10-19 and compared with both endpoints on 2026-10-20. This is neither
 * the JSON request length nor a documented size of Discord's internal representation.
 * See docs/discord-embed-limits.md for measurements and conservative allowances.
 */
public final class EmbedWebhookCost {

    // Keep the stricter webhook scale for both delivery paths, including bot messages.
    public static final int OBSERVED_BUDGET = 10172;
    // The packer does not know the destination webhook's display name (up to 80 code points).
    private static final int WEBHOOK_NAME_ALLOWANCE = 320;
    // A resolved webhook avatar reduced the measured text budget by another 36.
    private static final int WEBHOOK_AVATAR_ALLOWANCE = 128;
    private static final int MEDIA_ALLOWANCE = 128;

    private EmbedWebhookCost() {
    }

    public static int estimate(final List<PendingEmbed> embeds) {
        return estimate(DiscordWebhook.builder().embeds(EmbedRenderer.toDiscordEmbeds(embeds)).build());
    }

    public static int estimate(final DiscordWebhook webhook) {
        int total = WEBHOOK_NAME_ALLOWANCE + WEBHOOK_AVATAR_ALLOWANCE + text(webhook.content);
        if (webhook.embeds != null)
            for (final DiscordEmbed embed : webhook.embeds)
                total += embedCost(embed);
        return total;
    }

    public static boolean fits(final List<PendingEmbed> embeds) {
        return estimate(embeds) <= OBSERVED_BUDGET;
    }

    private static int embedCost(final DiscordEmbed embed) {
        int total = 24 + text(embed.title) + text(embed.description) + text(embed.url);
        // Use the largest measured RGB cost, including the zero emitted by DiscordEmbed.
        total += 7;
        if (embed.timestamp != null)
            total += 14;
        if (embed.footer != null) {
            total += text(embed.footer.text) + 1;
            if (embed.footer.icon_url != null)
                total += text(embed.footer.icon_url) + 3 + MEDIA_ALLOWANCE;
        }
        if (embed.author != null) {
            total += text(embed.author.name) + text(embed.author.url);
            if (embed.author.icon_url != null)
                total += text(embed.author.icon_url) + 2 + MEDIA_ALLOWANCE;
        }
        if (embed.image != null)
            total += text(embed.image.url) + 2 + MEDIA_ALLOWANCE;
        if (embed.thumbnail != null)
            total += text(embed.thumbnail.url) + 2 + MEDIA_ALLOWANCE;
        if (embed.fields != null)
            for (final DiscordEmbed.EmbedField field : embed.fields)
                total += 6 + text(field.name) + text(field.value) + (field.inline ? 4 : 0);
        return total;
    }

    private static int text(final String value) {
        if (value == null)
            return 0;
        final int bytes = DiscordLimits.utf8Bytes(value);
        // Reserve the longer observed string/length overhead even for short strings.
        return bytes + 5;
    }
}
