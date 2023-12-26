package net.teamfruit.eewbot.entity;

import discord4j.rest.util.Color;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DiscordEmbed {

    public String title;
    public String type;
    public String description;
    public String url;
    public String timestamp;
    public int color;
    public EmbedFooter footer;
    public EmbedImage image;
    public EmbedThumbnail thumbnail;
    public EmbedVideo video;
    public EmbedProvider provider;
    public EmbedAuthor author;
    public List<EmbedField> fields;

    public static class EmbedFooter {

        public String text;
        public String icon_url;
        public String proxy_icon_url;

        public EmbedFooter(final String text, final String icon_url, final String proxy_icon_url) {
            this.text = text;
            this.icon_url = icon_url;
            this.proxy_icon_url = proxy_icon_url;
        }
    }

    public static class EmbedImage {

        public String url;
        public String proxy_url;

        public EmbedImage(final String url, final String proxy_url) {
            this.url = url;
            this.proxy_url = proxy_url;
        }
    }

    public static class EmbedThumbnail {

        public String url;
        public String proxy_url;

        public EmbedThumbnail(final String url, final String proxy_url) {
            this.url = url;
            this.proxy_url = proxy_url;
        }
    }

    public static class EmbedVideo {

        public String url;
        public String proxy_url;

        public EmbedVideo(final String url, final String proxy_url) {
            this.url = url;
            this.proxy_url = proxy_url;
        }
    }

    public static class EmbedProvider {

        public String name;
        public String url;

        public EmbedProvider(final String name, final String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class EmbedAuthor {

        public String name;
        public String url;
        public String icon_url;
        public String proxy_icon_url;

        public EmbedAuthor(final String name, final String url, final String icon_url, final String proxy_icon_url) {
            this.name = name;
            this.url = url;
            this.icon_url = icon_url;
            this.proxy_icon_url = proxy_icon_url;
        }
    }

    public static class EmbedField {

        public String name;
        public String value;
        public boolean inline;

        public EmbedField(final String name, final String value, final boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }

    public static DiscordEmbed.Builder builder() {
        return new DiscordEmbed.Builder();
    }

    public static class Builder {

        private final DiscordEmbed embed;

        public Builder() {
            this.embed = new DiscordEmbed();
        }

        public Builder title(final String title) {
            this.embed.title = title;
            return this;
        }

        public Builder type(final String type) {
            this.embed.type = type;
            return this;
        }

        public Builder description(final String description) {
            this.embed.description = description;
            return this;
        }

        public Builder url(final String url) {
            this.embed.url = url;
            return this;
        }

        public Builder timestamp(final Instant timestamp) {
            this.embed.timestamp = timestamp.toString();
            return this;
        }

        public Builder color(final int color) {
            this.embed.color = color;
            return this;
        }

        public Builder color(final Color color) {
            return color(color.getRGB());
        }

        public Builder footer(final EmbedFooter footer) {
            this.embed.footer = footer;
            return this;
        }

        public Builder footer(final String text, final String icon_url, final String proxy_icon_url) {
            return footer(new EmbedFooter(text, icon_url, proxy_icon_url));
        }

        public Builder footer(final String text, final String icon_url) {
            return footer(text, icon_url, null);
        }

        public Builder image(final EmbedImage image) {
            this.embed.image = image;
            return this;
        }

        public Builder image(final String url, final String proxy_url) {
            return image(new EmbedImage(url, proxy_url));
        }

        public Builder image(final String url) {
            return image(url, null);
        }

        public Builder thumbnail(final EmbedThumbnail thumbnail) {
            this.embed.thumbnail = thumbnail;
            return this;
        }

        public Builder thumbnail(final String url, final String proxy_url) {
            return thumbnail(new EmbedThumbnail(url, proxy_url));
        }

        public Builder thumbnail(final String url) {
            return thumbnail(url, null);
        }

        public Builder video(final EmbedVideo video) {
            this.embed.video = video;
            return this;
        }

        public Builder video(final String url, final String proxy_url) {
            return video(new EmbedVideo(url, proxy_url));
        }

        public Builder video(final String url) {
            return video(url, null);
        }

        public Builder provider(final EmbedProvider provider) {
            this.embed.provider = provider;
            return this;
        }

        public Builder provider(final String name, final String url) {
            return provider(new EmbedProvider(name, url));
        }

        public Builder author(final EmbedAuthor author) {
            this.embed.author = author;
            return this;
        }

        public Builder author(final String name, final String url, final String icon_url, final String proxy_icon_url) {
            return author(new EmbedAuthor(name, url, icon_url, proxy_icon_url));
        }

        public Builder author(final String name, final String url, final String icon_url) {
            return author(name, url, icon_url, null);
        }

        public Builder addField(final EmbedField field) {
            if (this.embed.fields == null)
                this.embed.fields = new ArrayList<>();
            this.embed.fields.add(field);
            return this;
        }

        public Builder addField(final String name, final String value, final boolean inline) {
            return addField(new EmbedField(name, value, inline));
        }

        public DiscordEmbed build() {
            return this.embed;
        }
    }
}
