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

    public int byteCount() {
        int total = EmbedPacker.EMBED_JSON_OVERHEAD;
        total += property(this.title, 11);            // "title":"",
        total += property(this.description, 17);      // "description":"",
        total += property(this.url, 9);               // "url":"",
        total += property(this.footerText, 22);       // "footer":{"text":""},
        total += property(this.footerIcon, 14);       // "icon_url":"",
        total += property(this.image, 21);            // "image":{"url":""},
        total += property(this.thumbnail, 25);        // "thumbnail":{"url":""},
        total += property(this.authorName, 22);       // "author":{"name":""},
        total += property(this.authorUrl, 9);
        total += property(this.authorIcon, 14);
        if (this.color != null)
            total += 18;                              // "color":16777215,
        if (this.timestamp != null)
            total += 40;                              // "timestamp":"2024-01-01T00:00:00Z",
        if (!this.fields.isEmpty())
            total += EmbedPacker.FIELDS_ARRAY_JSON_OVERHEAD;
        for (final PendingField field : this.fields)
            total += EmbedPacker.FIELD_JSON_OVERHEAD
                    + DiscordLimits.jsonTextBytes(field.name())
                    + DiscordLimits.jsonTextBytes(field.value());
        return total;
    }

    private static int property(final String value, final int keyOverhead) {
        return value == null ? 0 : keyOverhead + DiscordLimits.jsonTextBytes(value);
    }
}
