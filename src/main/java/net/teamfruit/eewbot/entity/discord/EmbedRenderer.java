package net.teamfruit.eewbot.entity.discord;

import discord4j.core.spec.EmbedCreateSpec;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;

import java.util.List;

public final class EmbedRenderer {

    private EmbedRenderer() {
    }

    public static DiscordEmbed toDiscordEmbed(final PendingEmbed embed) {
        final DiscordEmbed.Builder builder = DiscordEmbed.builder();
        if (embed.title() != null)
            builder.title(embed.title());
        if (embed.description() != null)
            builder.description(embed.description());
        if (embed.url() != null)
            builder.url(embed.url());
        if (embed.timestamp() != null)
            builder.timestamp(embed.timestamp());
        if (embed.color() != null)
            builder.color(embed.color());
        if (embed.footerText() != null)
            builder.footer(embed.footerText(), embed.footerIcon());
        if (embed.image() != null)
            builder.image(embed.image());
        if (embed.thumbnail() != null)
            builder.thumbnail(embed.thumbnail());
        if (embed.authorName() != null)
            builder.author(embed.authorName(), embed.authorUrl(), embed.authorIcon());
        for (final PendingField field : embed.fields())
            builder.addField(field.name(), field.value(), field.inline());
        return builder.build();
    }

    public static EmbedCreateSpec toEmbedCreateSpec(final PendingEmbed embed) {
        final EmbedCreateSpec.Builder builder = EmbedCreateSpec.builder();
        if (embed.title() != null)
            builder.title(embed.title());
        if (embed.description() != null)
            builder.description(embed.description());
        if (embed.url() != null)
            builder.url(embed.url());
        if (embed.timestamp() != null)
            builder.timestamp(embed.timestamp());
        if (embed.color() != null)
            builder.color(embed.color());
        if (embed.footerText() != null)
            builder.footer(embed.footerText(), embed.footerIcon());
        if (embed.image() != null)
            builder.image(embed.image());
        if (embed.thumbnail() != null)
            builder.thumbnail(embed.thumbnail());
        if (embed.authorName() != null)
            builder.author(embed.authorName(), embed.authorUrl(), embed.authorIcon());
        for (final PendingField field : embed.fields())
            builder.addField(field.name(), field.value(), field.inline());
        return builder.build();
    }

    public static List<DiscordEmbed> toDiscordEmbeds(final List<PendingEmbed> embeds) {
        return embeds.stream().map(EmbedRenderer::toDiscordEmbed).toList();
    }

    public static List<EmbedCreateSpec> toEmbedCreateSpecs(final List<PendingEmbed> embeds) {
        return embeds.stream().map(EmbedRenderer::toEmbedCreateSpec).toList();
    }
}
