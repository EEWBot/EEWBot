package net.teamfruit.eewbot.i18n;

import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;

import java.time.Instant;

public class I18nEmbedCreateSpec {

    public static I18nEmbedCreateSpec.Builder builder(String lang) {
        return new I18nEmbedCreateSpec.Builder(lang, EmbedCreateSpec.builder());
    }

    public static class Builder {

        private final String lang;
        private final EmbedCreateSpec.Builder builder;
        private Builder(String lang, EmbedCreateSpec.Builder builder) {
            this.lang = lang;
            this.builder = builder;
        }

        public Builder title(String title) {
            this.builder.title(I18n.INSTANCE.get(this.lang, title));
            return this;
        }

        public Builder title(String title, Object... format) {
            this.builder.title(I18n.INSTANCE.format(this.lang, title, format));
            return this;
        }

        public Builder description(String description) {
            this.builder.description(I18n.INSTANCE.get(this.lang, description));
            return this;
        }

        public Builder description(String description, Object... format) {
            this.builder.description(I18n.INSTANCE.format(this.lang, description, format));
            return this;
        }

        public Builder url(String url) {
            this.builder.url(url);
            return this;
        }

        public Builder timestamp(Instant time) {
            this.builder.timestamp(time);
            return this;
        }

        public Builder color(Color color) {
            this.builder.color(color);
            return this;
        }

        public Builder footer(String text, String iconUrl) {
            this.builder.footer(I18n.INSTANCE.get(this.lang, text), iconUrl);
            return this;
        }

        public Builder footer(String text, String iconUrl, Object... format) {
            this.builder.footer(I18n.INSTANCE.format(this.lang, text, format), iconUrl);
            return this;
        }

        public Builder image(String image) {
            this.builder.image(image);
            return this;
        }

        public Builder thumbnail(String thumbnail) {
            this.builder.thumbnail(thumbnail);
            return this;
        }

        public Builder author(String name, String url, String iconUrl) {
            this.builder.author(I18n.INSTANCE.get(this.lang, name), url, iconUrl);
            return this;
        }

        public Builder author(String name, String url, String iconUrl, Object... format) {
            this.builder.author(I18n.INSTANCE.format(this.lang, name, format), url, iconUrl);
            return this;
        }

        public Builder addField(String name, String value, boolean inline) {
            this.builder.addField(I18n.INSTANCE.get(this.lang, name), I18n.INSTANCE.get(this.lang, value), inline);
            return this;
        }

        public Builder addField(String name, String value, boolean inline, Object... format) {
            this.builder.addField(I18n.INSTANCE.format(this.lang, name, format), I18n.INSTANCE.format(this.lang, value, format), inline);
            return this;
        }

        public EmbedCreateSpec build() {
            return this.builder.build();
        }
    }
}
