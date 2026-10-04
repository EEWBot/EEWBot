package net.teamfruit.eewbot.entity.discord;

import java.util.List;

/**
 * Discord Components V2 before message limits are applied. Names and nesting follow Discord's
 * components; text content is supplied as-is, without translation or Markdown formatting.
 */
public sealed interface PendingComponent permits PendingComponent.Container, PendingComponent.Section,
        PendingComponent.TextDisplay, PendingComponent.MediaGallery, PendingComponent.Separator {

    record Container(List<PendingComponent> components, Integer accentColor, boolean spoiler)
            implements PendingComponent {
        public Container {
            components = List.copyOf(components);
            if (accentColor != null && (accentColor < 0 || accentColor > 0xffffff))
                throw new IllegalArgumentException("accentColor must be an RGB value");
        }

        public Container withComponents(final List<PendingComponent> components) {
            return new Container(components, this.accentColor, this.spoiler);
        }

        public static ContainerBuilder builder() {
            return new ContainerBuilder();
        }

        public static Container of(final Integer accentColor, final boolean spoiler,
                                   final PendingComponent... components) {
            return new Container(List.of(components), accentColor, spoiler);
        }
    }

    record Section(List<TextDisplay> components, Accessory accessory) implements PendingComponent {
        public Section {
            components = List.copyOf(components);
        }

        public static Section of(final Accessory accessory, final TextDisplay... components) {
            return new Section(List.of(components), accessory);
        }
    }

    record TextDisplay(String content) implements PendingComponent {
        public static TextDisplay of(final String content) {
            return new TextDisplay(content);
        }
    }

    record MediaGallery(List<MediaGalleryItem> items) implements PendingComponent {
        public MediaGallery {
            items = List.copyOf(items);
        }

        public static MediaGallery of(final MediaGalleryItem... items) {
            return new MediaGallery(List.of(items));
        }
    }

    record Separator(boolean divider, Spacing spacing) implements PendingComponent {
        public static Separator of(final boolean divider, final Spacing spacing) {
            return new Separator(divider, spacing);
        }
    }

    sealed interface Accessory permits Thumbnail {
    }

    record Thumbnail(String url, String description, boolean spoiler) implements Accessory {
        public static Thumbnail of(final String url, final String description, final boolean spoiler) {
            return new Thumbnail(url, description, spoiler);
        }
    }

    record MediaGalleryItem(String url, String description, boolean spoiler) {
        public static MediaGalleryItem of(final String url, final String description, final boolean spoiler) {
            return new MediaGalleryItem(url, description, spoiler);
        }
    }

    enum Spacing {
        SMALL,
        LARGE
    }
}
