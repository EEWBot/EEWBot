package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;

import java.time.Instant;
import java.util.List;

public record PendingEmbed(
        String title,
        String description,
        String url,
        Instant timestamp,
        Color color,
        String footerText,
        String footerIcon,
        String image,
        String thumbnail,
        String authorName,
        String authorUrl,
        String authorIcon,
        List<PendingField> fields
) {

    public record PendingField(String name, String value, boolean inline) {
    }

    public static PendingEmbed empty() {
        return new PendingEmbed(null, null, null, null, null, null, null, null, null, null, null, null, List.of());
    }

    public PendingEmbed withFields(final List<PendingField> fields) {
        return new PendingEmbed(this.title, this.description, this.url, this.timestamp, this.color,
                this.footerText, this.footerIcon, this.image, this.thumbnail,
                this.authorName, this.authorUrl, this.authorIcon, fields);
    }

    public PendingEmbed withDescription(final String description) {
        return new PendingEmbed(this.title, description, this.url, this.timestamp, this.color,
                this.footerText, this.footerIcon, this.image, this.thumbnail,
                this.authorName, this.authorUrl, this.authorIcon, this.fields);
    }

    public PendingEmbed headChromeOnly() {
        return new PendingEmbed(this.title, this.description, this.url, null, this.color,
                null, null, null, this.thumbnail, null, null, null, this.fields);
    }

    public PendingEmbed tailChromeOnly() {
        return new PendingEmbed(null, null, null, this.timestamp, this.color,
                this.footerText, this.footerIcon, this.image, null,
                this.authorName, this.authorUrl, this.authorIcon, this.fields);
    }

    public PendingEmbed colorChromeOnly() {
        return new PendingEmbed(null, null, null, null, this.color,
                null, null, null, null, null, null, null, this.fields);
    }

    /**
     * {@link DiscordLimits#MAX_TOTAL_CHARS_PER_MESSAGE} に算入されるコードポイント数
     */
    public int charCount() {
        int total = DiscordLimits.codePoints(this.title)
                + DiscordLimits.codePoints(this.description)
                + DiscordLimits.codePoints(this.footerText)
                + DiscordLimits.codePoints(this.authorName);
        for (final PendingField field : this.fields)
            total += DiscordLimits.codePoints(field.name()) + DiscordLimits.codePoints(field.value());
        return total;
    }

}
