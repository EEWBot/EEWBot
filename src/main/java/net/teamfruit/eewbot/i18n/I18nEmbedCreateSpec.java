package net.teamfruit.eewbot.i18n;

import discord4j.core.spec.EmbedCreateSpec;

public class I18nEmbedCreateSpec extends EmbedCreateSpec {

	private final String lang;

	public I18nEmbedCreateSpec(final String lang) {
		this.lang = lang;
	}

	@Override
	public EmbedCreateSpec setTitle(final String title) {
		return super.setTitle(I18n.INSTANCE.get(this.lang, title));
	}

	public EmbedCreateSpec setTitle(final String title, final Object... format) {
		return super.setTitle(I18n.INSTANCE.format(this.lang, title, format));
	}

	@Override
	public EmbedCreateSpec setDescription(final String description) {
		return super.setDescription(I18n.INSTANCE.get(this.lang, description));
	}

	public EmbedCreateSpec setDescription(final String description, final Object... format) {
		return super.setDescription(I18n.INSTANCE.format(this.lang, description, format));
	}

	@Override
	public EmbedCreateSpec setFooter(final String text, final String iconUrl) {
		return super.setFooter(I18n.INSTANCE.get(this.lang, text), iconUrl);
	}

	public EmbedCreateSpec setFooter(final String text, final String iconUrl, final Object... format) {
		return super.setFooter(I18n.INSTANCE.format(this.lang, text, format), iconUrl);
	}

	@Override
	public EmbedCreateSpec setAuthor(final String name, final String url, final String iconUrl) {
		return super.setAuthor(I18n.INSTANCE.get(this.lang, name), url, iconUrl);
	}

	public EmbedCreateSpec setAuthor(final String name, final String url, final String iconUrl, final Object... format) {
		return super.setAuthor(I18n.INSTANCE.format(this.lang, name, format), url, iconUrl);
	}

	@Override
	public EmbedCreateSpec addField(final String name, final String value, final boolean inline) {
		return super.addField(I18n.INSTANCE.get(this.lang, name), I18n.INSTANCE.get(this.lang, value), inline);
	}

	public EmbedCreateSpec addField(final String name, final String value, final boolean inline, final Object... format) {
		return super.addField(I18n.INSTANCE.format(this.lang, name, format), I18n.INSTANCE.format(this.lang, value, format), inline);
	}

}
