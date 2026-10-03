package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.discord.PendingComponent.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a Discord Container explicitly. Text is already translated and formatted by the caller.
 * Limits are applied later by {@link ComponentPacker}, so oversized content can be split without loss.
 * Top-level components can also be created directly with {@code TextDisplay.of(...)},
 * {@code Section.of(...)}, etc., without a Container.
 */
public final class ContainerBuilder {

    private final List<PendingComponent> components = new ArrayList<>();
    private Integer accentColor;
    private boolean spoiler;

    public ContainerBuilder add(final PendingComponent component) {
        this.components.add(Objects.requireNonNull(component));
        return this;
    }

    public ContainerBuilder textDisplay(final String content) {
        if (content != null && !content.isBlank())
            add(TextDisplay.of(content));
        return this;
    }

    public ContainerBuilder section(final Accessory accessory, final TextDisplay... components) {
        return add(Section.of(accessory, components));
    }

    public ContainerBuilder mediaGallery(final MediaGalleryItem... items) {
        return add(MediaGallery.of(items));
    }

    public ContainerBuilder separator() {
        return separator(true, Spacing.SMALL);
    }

    public ContainerBuilder separator(final boolean divider, final Spacing spacing) {
        return add(Separator.of(divider, spacing));
    }

    public ContainerBuilder accentColor(final int rgb) {
        if (rgb < 0 || rgb > 0xffffff)
            throw new IllegalArgumentException("accentColor must be an RGB value");
        this.accentColor = rgb;
        return this;
    }

    public ContainerBuilder accentColor(final Color color) {
        return accentColor(color.getRGB());
    }

    public ContainerBuilder spoiler(final boolean spoiler) {
        this.spoiler = spoiler;
        return this;
    }

    public Container build() {
        return new Container(this.components, this.accentColor, this.spoiler);
    }
}
