package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;
import net.teamfruit.eewbot.i18n.I18n;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class I18nEmbedBuilder implements IEmbedBuilder {

    private final String lang;
    private final I18n i18n;

    private String title;
    private String description;
    private String url;
    private Instant timestamp;
    private Color color;
    private String footerText;
    private String footerIcon;
    private String image;
    private String thumbnail;
    private String authorName;
    private String authorUrl;
    private String authorIcon;
    private final List<PendingField> fields = new ArrayList<>();

    public I18nEmbedBuilder(final String lang, final I18n i18n) {
        this.lang = lang;
        this.i18n = i18n;
    }

    public static I18nEmbedBuilder builder(final String lang, final I18n i18n) {
        return new I18nEmbedBuilder(lang, i18n);
    }

    private String get(final String key) {
        return this.i18n.get(this.lang, key);
    }

    private String format(final String key, final Object... args) {
        return this.i18n.format(this.lang, key, args);
    }

    @Override
    public IEmbedBuilder title(final String title) {
        this.title = get(title);
        return this;
    }

    @Override
    public IEmbedBuilder title(final String title, final Object... format) {
        this.title = format(title, format);
        return this;
    }

    @Override
    public IEmbedBuilder description(final String description) {
        this.description = get(description);
        return this;
    }

    @Override
    public IEmbedBuilder description(final String description, final Object... format) {
        this.description = format(description, format);
        return this;
    }

    @Override
    public IEmbedBuilder url(final String url) {
        this.url = url;
        return this;
    }

    @Override
    public IEmbedBuilder timestamp(final Instant time) {
        this.timestamp = time;
        return this;
    }

    @Override
    public IEmbedBuilder color(final Color color) {
        this.color = color;
        return this;
    }

    @Override
    public IEmbedBuilder footer(final String text, final String iconUrl) {
        this.footerText = get(text);
        this.footerIcon = iconUrl;
        return this;
    }

    @Override
    public IEmbedBuilder footer(final String text, final String iconUrl, final Object... format) {
        this.footerText = format(text, format);
        this.footerIcon = iconUrl;
        return this;
    }

    @Override
    public IEmbedBuilder image(final String image) {
        this.image = image;
        return this;
    }

    @Override
    public IEmbedBuilder thumbnail(final String thumbnail) {
        this.thumbnail = thumbnail;
        return this;
    }

    @Override
    public IEmbedBuilder author(final String name, final String url, final String iconUrl) {
        this.authorName = get(name);
        this.authorUrl = url;
        this.authorIcon = iconUrl;
        return this;
    }

    @Override
    public IEmbedBuilder author(final String name, final String url, final String iconUrl, final Object... format) {
        this.authorName = format(name, format);
        this.authorUrl = url;
        this.authorIcon = iconUrl;
        return this;
    }

    @Override
    public IEmbedBuilder addField(final String name, final String value, final boolean inline) {
        this.fields.add(new PendingField(get(name), get(value), inline));
        return this;
    }

    @Override
    public IEmbedBuilder addField(final String name, final String value, final boolean inline, final Object... format) {
        this.fields.add(new PendingField(format(name, format), format(value, format), inline));
        return this;
    }

    @Override
    public PendingEmbed toPending() {
        return new PendingEmbed(this.title, this.description, this.url, this.timestamp, this.color,
                this.footerText, this.footerIcon, this.image, this.thumbnail,
                this.authorName, this.authorUrl, this.authorIcon, List.copyOf(this.fields));
    }
}
