package net.teamfruit.eewbot.i18n;

import java.awt.Color;
import java.time.Instant;

import discord4j.core.spec.EmbedCreateSpec;

public class I18nEmbedCreateSpecWrapper {

	private final String lang;
	private final EmbedCreateSpec spec;

	public I18nEmbedCreateSpecWrapper(final String lang, final EmbedCreateSpec spec) {
		this.lang = lang;
		this.spec = spec;
	}

	public EmbedCreateSpec get() {
		return this.spec;
	}

	public I18nEmbedCreateSpecWrapper setTitle(final String title) {
		this.spec.setTitle(I18n.INSTANCE.get(this.lang, title));
		return this;
	}

	public I18nEmbedCreateSpecWrapper setTitle(final String title, final Object... format) {
		this.spec.setTitle(I18n.INSTANCE.format(this.lang, title, format));
		return this;
	}

	public I18nEmbedCreateSpecWrapper setDescription(final String description) {
		this.spec.setDescription(I18n.INSTANCE.get(this.lang, description));
		return this;
	}

	public I18nEmbedCreateSpecWrapper setDescription(final String description, final Object... format) {
		this.spec.setDescription(I18n.INSTANCE.format(this.lang, description, format));
		return this;
	}

	public I18nEmbedCreateSpecWrapper setUrl(final String url) {
		this.spec.setUrl(url);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setTimestamp(final Instant timestamp) {
		this.spec.setTimestamp(timestamp);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setColor(final Color color) {
		this.spec.setColor(color);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setFooter(final String text, final String iconUrl) {
		this.spec.setFooter(I18n.INSTANCE.get(this.lang, text), iconUrl);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setFooter(final String text, final String iconUrl, final Object... format) {
		this.spec.setFooter(I18n.INSTANCE.format(this.lang, text, format), iconUrl);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setImage(final String url) {
		this.spec.setImage(url);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setThumbnail(final String url) {
		this.spec.setThumbnail(url);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setAuthor(final String name, final String url, final String iconUrl) {
		this.spec.setAuthor(I18n.INSTANCE.get(this.lang, name), url, iconUrl);
		return this;
	}

	public I18nEmbedCreateSpecWrapper setAuthor(final String name, final String url, final String iconUrl, final Object... format) {
		this.spec.setAuthor(I18n.INSTANCE.format(this.lang, name, format), url, iconUrl);
		return this;
	}

	public I18nEmbedCreateSpecWrapper addField(final String name, final String value, final boolean inline) {
		this.spec.addField(I18n.INSTANCE.get(this.lang, name), I18n.INSTANCE.get(this.lang, value), inline);
		return this;
	}

	public I18nEmbedCreateSpecWrapper addField(final String name, final String value, final boolean inline, final Object... format) {
		this.spec.addField(I18n.INSTANCE.format(this.lang, name, format), I18n.INSTANCE.format(this.lang, value, format), inline);
		return this;
	}

}
