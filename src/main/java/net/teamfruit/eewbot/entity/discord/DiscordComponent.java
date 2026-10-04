package net.teamfruit.eewbot.entity.discord;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public sealed interface DiscordComponent permits DiscordComponent.Container, DiscordComponent.Section,
        DiscordComponent.TextDisplay, DiscordComponent.MediaGallery, DiscordComponent.Separator,
        DiscordComponent.Thumbnail {

    record Container(int type, List<DiscordComponent> components,
                     @SerializedName("accent_color") Integer accentColor, boolean spoiler)
            implements DiscordComponent {
    }

    record Section(int type, List<DiscordComponent> components, DiscordComponent accessory)
            implements DiscordComponent {
    }

    record TextDisplay(int type, String content) implements DiscordComponent {
    }

    record MediaGallery(int type, List<MediaItem> items) implements DiscordComponent {
    }

    record Separator(int type, boolean divider, int spacing) implements DiscordComponent {
    }

    record Thumbnail(int type, Media media, String description, boolean spoiler) implements DiscordComponent {
    }

    record Media(String url) {
    }

    record MediaItem(Media media, String description, boolean spoiler) {
    }
}
