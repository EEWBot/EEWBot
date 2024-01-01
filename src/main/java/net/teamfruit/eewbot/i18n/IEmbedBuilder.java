package net.teamfruit.eewbot.i18n;

import discord4j.rest.util.Color;

import java.time.Instant;

public interface IEmbedBuilder<T> {

    IEmbedBuilder<T> title(String title);

    IEmbedBuilder<T> title(String title, Object... format);

    IEmbedBuilder<T> description(String description);

    IEmbedBuilder<T> description(String description, Object... format);

    IEmbedBuilder<T> url(String url);

    IEmbedBuilder<T> timestamp(Instant time);

    IEmbedBuilder<T> color(Color color);

    IEmbedBuilder<T> footer(String text, String iconUrl);

    IEmbedBuilder<T> footer(String text, String iconUrl, Object... format);

    IEmbedBuilder<T> image(String image);

    IEmbedBuilder<T> thumbnail(String thumbnail);

    IEmbedBuilder<T> author(String name, String url, String iconUrl);

    IEmbedBuilder<T> author(String name, String url, String iconUrl, Object... format);

    IEmbedBuilder<T> addField(String name, String value, boolean inline);

    IEmbedBuilder<T> addField(String name, String value, boolean inline, Object... format);

    T build();
}
