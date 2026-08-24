package net.teamfruit.eewbot.entity.discord;

import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;

import java.time.Instant;

public interface IEmbedBuilder {

    IEmbedBuilder title(String title);

    IEmbedBuilder title(String title, Object... format);

    IEmbedBuilder description(String description);

    IEmbedBuilder description(String description, Object... format);

    IEmbedBuilder url(String url);

    IEmbedBuilder timestamp(Instant time);

    IEmbedBuilder color(Color color);

    IEmbedBuilder footer(String text, String iconUrl);

    IEmbedBuilder footer(String text, String iconUrl, Object... format);

    IEmbedBuilder image(String image);

    IEmbedBuilder thumbnail(String thumbnail);

    IEmbedBuilder author(String name, String url, String iconUrl);

    IEmbedBuilder author(String name, String url, String iconUrl, Object... format);

    IEmbedBuilder addField(String name, String value, boolean inline);

    IEmbedBuilder addField(String name, String value, boolean inline, Object... format);

    PendingEmbed toPending();

    default EmbedCreateSpec toEmbedCreateSpec() {
        return EmbedRenderer.toEmbedCreateSpec(EmbedPacker.normalize(toPending()));
    }

    default DiscordEmbed toDiscordEmbed() {
        return EmbedRenderer.toDiscordEmbed(EmbedPacker.normalize(toPending()));
    }
}
