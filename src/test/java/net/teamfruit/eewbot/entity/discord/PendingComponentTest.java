package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.entity.discord.PendingComponent.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingComponentTest {

    @Test
    void sectionCannotBeChangedThroughSourceOrAccessorLists() {
        TextDisplay text = TextDisplay.of("Section text");
        List<TextDisplay> candidate = new ArrayList<>(List.of(text));
        Section section = new Section(candidate, Thumbnail.of("https://example.com/thumb.png", null, false));

        candidate.clear();
        assertThatThrownBy(() -> section.components().clear()).isInstanceOf(UnsupportedOperationException.class);

        assertThat(ComponentValidator.isValid(List.of(section))).isTrue();
        DiscordComponent.Section rendered = (DiscordComponent.Section) ComponentRenderer.toWebhook(List.of(section)).getFirst();
        assertThat(rendered.components()).containsExactly(new DiscordComponent.TextDisplay(10, text.content()));
    }

    @Test
    void galleryCannotBeChangedThroughSourceOrAccessorLists() {
        MediaGalleryItem item = MediaGalleryItem.of("https://example.com/map.png", null, false);
        List<MediaGalleryItem> candidate = new ArrayList<>(List.of(item));
        MediaGallery gallery = new MediaGallery(candidate);

        candidate.clear();
        assertThatThrownBy(() -> gallery.items().clear()).isInstanceOf(UnsupportedOperationException.class);

        assertThat(ComponentValidator.isValid(List.of(gallery))).isTrue();
        DiscordComponent.MediaGallery rendered = (DiscordComponent.MediaGallery) ComponentRenderer.toWebhook(List.of(gallery)).getFirst();
        assertThat(rendered.items()).containsExactly(new DiscordComponent.MediaItem(
                new DiscordComponent.Media(item.url()), null, false));
    }
}
