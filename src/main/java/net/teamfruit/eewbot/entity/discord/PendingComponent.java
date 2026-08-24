package net.teamfruit.eewbot.entity.discord;

import java.util.List;

/**
 * Discord-independent representation of the Components V2 subset used by EEWBot.
 */
public sealed interface PendingComponent permits PendingComponent.Container, PendingComponent.Section,
        PendingComponent.Text, PendingComponent.MediaGallery, PendingComponent.Separator {

    record Container(List<PendingComponent> children, Integer accentColor, boolean spoiler)
            implements PendingComponent {
        public Container {
            children = List.copyOf(children);
            if (accentColor != null && (accentColor < 0 || accentColor > 0xffffff))
                throw new IllegalArgumentException("accentColor must be an RGB value");
        }

        public Container withChildren(final List<PendingComponent> children) {
            return new Container(children, this.accentColor, this.spoiler);
        }
    }

    record Section(List<Text> children, Accessory accessory) implements PendingComponent {
        public Section {
            children = List.copyOf(children);
        }
    }

    record Text(String content) implements PendingComponent {
    }

    record MediaGallery(List<MediaItem> items) implements PendingComponent {
        public MediaGallery {
            items = List.copyOf(items);
        }
    }

    record Separator(boolean divider, Spacing spacing) implements PendingComponent {
    }

    sealed interface Accessory permits Thumbnail {
    }

    record Thumbnail(String url, String description, boolean spoiler) implements Accessory {
    }

    record MediaItem(String url, String description, boolean spoiler) {
    }

    enum Spacing {
        SMALL,
        LARGE
    }
}
