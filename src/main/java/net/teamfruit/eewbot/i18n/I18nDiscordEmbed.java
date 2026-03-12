package net.teamfruit.eewbot.i18n;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.discord.DiscordEmbed;

import java.time.Instant;

public class I18nDiscordEmbed {

    public static I18nDiscordEmbed.Builder builder(String lang, I18n i18n) {
        return new I18nDiscordEmbed.Builder(lang, i18n, DiscordEmbed.builder());
    }

    public static class Builder implements IEmbedBuilder<DiscordEmbed> {

        private final String lang;
        private final I18n i18n;
        private final DiscordEmbed.Builder builder;

        private Builder(String lang, I18n i18n, DiscordEmbed.Builder builder) {
            this.lang = lang;
            this.i18n = i18n;
            this.builder = builder;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> title(String title) {
            this.builder.title(this.i18n.get(this.lang, title));
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> title(String title, Object... format) {
            this.builder.title(this.i18n.format(this.lang, title, format));
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> description(String description) {
            this.builder.description(this.i18n.get(this.lang, description));
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> description(String description, Object... format) {
            this.builder.description(this.i18n.format(this.lang, description, format));
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> url(String url) {
            this.builder.url(url);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> timestamp(Instant time) {
            this.builder.timestamp(time);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> color(Color color) {
            this.builder.color(color);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> footer(String text, String iconUrl) {
            this.builder.footer(this.i18n.get(this.lang, text), iconUrl);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> footer(String text, String iconUrl, Object... format) {
            this.builder.footer(this.i18n.format(this.lang, text, format), iconUrl);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> image(String image) {
            this.builder.image(image);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> thumbnail(String thumbnail) {
            this.builder.thumbnail(thumbnail);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> author(String name, String url, String iconUrl) {
            this.builder.author(this.i18n.get(this.lang, name), url, iconUrl);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> author(String name, String url, String iconUrl, Object... format) {
            this.builder.author(this.i18n.format(this.lang, name, format), url, iconUrl);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> addField(String name, String value, boolean inline) {
            this.builder.addField(this.i18n.get(this.lang, name), this.i18n.get(this.lang, value), inline);
            return this;
        }

        @Override
        public IEmbedBuilder<DiscordEmbed> addField(String name, String value, boolean inline, Object... format) {
            this.builder.addField(this.i18n.format(this.lang, name, format), this.i18n.format(this.lang, value, format), inline);
            return this;
        }

        @Override
        public DiscordEmbed build() {
            return this.builder.build();
        }
    }

}
