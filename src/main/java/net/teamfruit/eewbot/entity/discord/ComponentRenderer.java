package net.teamfruit.eewbot.entity.discord;

import discord4j.core.object.component.*;
import discord4j.rest.util.Color;

import java.util.List;

public final class ComponentRenderer {

    private ComponentRenderer() {
    }

    public static List<TopLevelMessageComponent> toDiscord4J(final List<PendingComponent> components) {
        return components.stream().map(ComponentRenderer::toTopLevel).toList();
    }

    private static TopLevelMessageComponent toTopLevel(final PendingComponent component) {
        if (component instanceof PendingComponent.Container container) {
            final List<ICanBeUsedInContainerComponent> children = container.children().stream()
                    .map(ComponentRenderer::toContainerChild).toList();
            if (container.accentColor() != null)
                return Container.of(Color.of(container.accentColor()), container.spoiler(), children);
            return Container.of(container.spoiler(), children);
        }
        if (component instanceof PendingComponent.Text text)
            return TextDisplay.of(text.content());
        if (component instanceof PendingComponent.Section section)
            return toSection(section);
        if (component instanceof PendingComponent.MediaGallery gallery)
            return toGallery(gallery);
        if (component instanceof PendingComponent.Separator separator)
            return toSeparator(separator);
        throw new IllegalArgumentException("Unsupported component: " + component);
    }

    private static ICanBeUsedInContainerComponent toContainerChild(final PendingComponent component) {
        return (ICanBeUsedInContainerComponent) toTopLevel(component);
    }

    private static Section toSection(final PendingComponent.Section section) {
        final List<ICanBeUsedInSectionComponent> children = section.children().stream()
                .map(text -> (ICanBeUsedInSectionComponent) TextDisplay.of(text.content())).toList();
        final PendingComponent.Accessory accessory = section.accessory();
        if (accessory instanceof PendingComponent.Thumbnail thumbnail)
            return Section.of(Thumbnail.of(UnfurledMediaItem.of(thumbnail.url()), thumbnail.description(), thumbnail.spoiler()), children);
        throw new IllegalArgumentException("Unsupported section accessory: " + accessory);
    }

    private static MediaGallery toGallery(final PendingComponent.MediaGallery gallery) {
        return MediaGallery.of(gallery.items().stream()
                .map(item -> MediaGalleryItem.of(UnfurledMediaItem.of(item.url()), item.description(), item.spoiler()))
                .toList());
    }

    private static Separator toSeparator(final PendingComponent.Separator separator) {
        return Separator.of(separator.divider(), separator.spacing() == PendingComponent.Spacing.LARGE
                ? Separator.SpacingSize.LARGE : Separator.SpacingSize.SMALL);
    }

    public static List<DiscordComponent> toWebhook(final List<PendingComponent> components) {
        return components.stream().map(ComponentRenderer::toWebhook).toList();
    }

    private static DiscordComponent toWebhook(final PendingComponent component) {
        if (component instanceof PendingComponent.Container container)
            return new DiscordComponent.Container(17, container.children().stream().map(ComponentRenderer::toWebhook).toList(),
                    container.accentColor(), container.spoiler());
        if (component instanceof PendingComponent.Text text)
            return new DiscordComponent.TextDisplay(10, text.content());
        if (component instanceof PendingComponent.Section section)
            return new DiscordComponent.Section(9, section.children().stream().map(ComponentRenderer::toWebhook).toList(),
                    toWebhookAccessory(section.accessory()));
        if (component instanceof PendingComponent.MediaGallery gallery)
            return new DiscordComponent.MediaGallery(12, gallery.items().stream()
                    .map(item -> new DiscordComponent.MediaItem(new DiscordComponent.Media(item.url()), item.description(), item.spoiler()))
                    .toList());
        if (component instanceof PendingComponent.Separator separator)
            return new DiscordComponent.Separator(14, separator.divider(), separator.spacing() == PendingComponent.Spacing.LARGE ? 2 : 1);
        throw new IllegalArgumentException("Unsupported component: " + component);
    }

    private static DiscordComponent toWebhookAccessory(final PendingComponent.Accessory accessory) {
        if (accessory instanceof PendingComponent.Thumbnail thumbnail)
            return new DiscordComponent.Thumbnail(11, new DiscordComponent.Media(thumbnail.url()),
                    thumbnail.description(), thumbnail.spoiler());
        throw new IllegalArgumentException("Unsupported accessory: " + accessory);
    }
}
