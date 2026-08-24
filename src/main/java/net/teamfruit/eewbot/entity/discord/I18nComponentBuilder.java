package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.entity.discord.PendingComponent.Container;
import net.teamfruit.eewbot.entity.discord.PendingComponent.MediaGallery;
import net.teamfruit.eewbot.entity.discord.PendingComponent.MediaItem;
import net.teamfruit.eewbot.entity.discord.PendingComponent.Text;
import net.teamfruit.eewbot.i18n.I18n;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class I18nComponentBuilder implements IComponentBuilder {

    private final String lang;
    private final I18n i18n;
    private final List<PendingComponent> children = new ArrayList<>();
    private Integer accentColor;
    private String footer;
    private Instant timestamp;

    public I18nComponentBuilder(final String lang, final I18n i18n) {
        this.lang = lang;
        this.i18n = i18n;
    }

    public static I18nComponentBuilder builder(final String lang, final I18n i18n) {
        return new I18nComponentBuilder(lang, i18n);
    }

    private String resolve(final String key, final Object... args) {
        return args.length == 0 ? this.i18n.get(this.lang, key) : this.i18n.format(this.lang, key, args);
    }

    @Override
    public IComponentBuilder heading(final String key, final Object... format) {
        this.children.add(new Text("# " + resolve(key, format)));
        return this;
    }

    @Override
    public IComponentBuilder text(final String key, final Object... format) {
        return rawText(resolve(key, format));
    }

    @Override
    public IComponentBuilder rawText(final String text) {
        if (text != null && !text.isBlank())
            this.children.add(new Text(text));
        return this;
    }

    @Override
    public IComponentBuilder detail(final String labelKey, final String value) {
        final String label = resolve(labelKey);
        return rawText(label.isBlank() ? value : "**" + label + "**\n" + value);
    }

    @Override
    public IComponentBuilder detail(final String labelKey, final String valueKey, final Object... format) {
        final String label = resolve(labelKey, format);
        final String value = resolve(valueKey, format);
        return rawText(label.isBlank() ? value : "**" + label + "**\n" + value);
    }

    @Override
    public IComponentBuilder separator() {
        this.children.add(new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL));
        return this;
    }

    @Override
    public IComponentBuilder media(final String url, final String description) {
        if (url != null)
            this.children.add(new MediaGallery(List.of(new MediaItem(url, description, false))));
        return this;
    }

    @Override
    public IComponentBuilder accent(final int rgb) {
        this.accentColor = rgb & 0xffffff;
        return this;
    }

    @Override
    public IComponentBuilder footer(final String text) {
        this.footer = resolve(text);
        return this;
    }

    @Override
    public IComponentBuilder timestamp(final Instant time) {
        this.timestamp = time;
        return this;
    }

    @Override
    public Container build() {
        final List<PendingComponent> result = new ArrayList<>(this.children);
        if (this.footer != null || this.timestamp != null) {
            final StringBuilder line = new StringBuilder("-# ");
            if (this.footer != null)
                line.append(this.footer);
            if (this.timestamp != null) {
                if (this.footer != null)
                    line.append(" • ");
                line.append("<t:").append(this.timestamp.getEpochSecond()).append(":F>");
            }
            result.add(new Text(line.toString()));
        }
        return new Container(result, this.accentColor, false);
    }
}
