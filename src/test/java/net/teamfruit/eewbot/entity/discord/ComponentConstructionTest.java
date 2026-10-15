package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.discord.PendingComponent.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentConstructionTest {

    @Test
    void explicitComponentsPreserveTopLevelLayoutInBothDeliveryPaths() {
        Thumbnail thumbnail = Thumbnail.of("https://example.com/thumb.png", "preview", true);
        Entity entity = (lang, ctx) -> List.of(
                TextDisplay.of("# Title"),
                Section.of(thumbnail, TextDisplay.of("Section text")),
                Container.builder().accentColor(0x123456).spoiler(true)
                        .mediaGallery(MediaGalleryItem.of("https://example.com/map.png", null, false))
                        .separator()
                        .textDisplay("-# Footer")
                        .build());

        List<DiscordWebhook> webhooks = entity.createWebhooks("ja_jp", null);
        var messages = entity.createMessages("ja_jp", null);
        List<DiscordComponent> components = webhooks.stream().flatMap(webhook -> webhook.components.stream()).toList();
        assertThat(components).extracting("type").containsExactly(10, 9, 17);
        assertThat(messages).hasSameSizeAs(webhooks);
        for (int i = 0; i < messages.size(); i++) {
            var request = messages.get(i).asRequest().getJsonPayload();
            assertThat(request.flags().get() & ComponentLimits.IS_COMPONENTS_V2).isNotZero();
            assertThat(request.components().get()).extracting("type")
                    .containsExactlyElementsOf(webhooks.get(i).components.stream()
                            .map(component -> component instanceof DiscordComponent.TextDisplay ? 10
                                    : component instanceof DiscordComponent.Section ? 9 : 17).toList());
        }
        DiscordComponent.Section section = (DiscordComponent.Section) components.get(1);
        assertThat(section.components()).containsExactly(new DiscordComponent.TextDisplay(10, "Section text"));
        assertThat(section.accessory()).isEqualTo(new DiscordComponent.Thumbnail(11,
                new DiscordComponent.Media(thumbnail.url()), "preview", true));
        DiscordComponent.Container container = (DiscordComponent.Container) components.get(2);
        assertThat(container.accentColor()).isEqualTo(0x123456);
        assertThat(container.spoiler()).isTrue();
        assertThat(container.components()).containsExactly(
                new DiscordComponent.MediaGallery(12, List.of(new DiscordComponent.MediaItem(
                        new DiscordComponent.Media("https://example.com/map.png"), null, false))),
                new DiscordComponent.Separator(14, true, 1),
                new DiscordComponent.TextDisplay(10, "-# Footer"));
    }

    @Test
    void containerPaginationPreservesCallerFormattingAndOptions() {
        String source = "# Custom heading\n" + "😀".repeat(4001) + "\n-# Custom footer";
        Container container = Container.builder().accentColor(0x123456).spoiler(true)
                .textDisplay(source).build();
        List<List<PendingComponent>> messages = ComponentPacker.pack(List.of(container));

        assertThat(messages).hasSizeGreaterThan(1);
        StringBuilder text = new StringBuilder();
        for (List<PendingComponent> message : messages) {
            assertThat(ComponentPacker.fit(message)).isEqualTo(ComponentPacker.Fit.SAFE);
            Container page = (Container) message.getFirst();
            assertThat(page.accentColor()).isEqualTo(0x123456);
            assertThat(page.spoiler()).isTrue();
            page.components().forEach(component -> text.append(((TextDisplay) component).content()));
        }
        assertThat(text.toString()).isEqualTo(source);
    }

    @Test
    void sectionPaginationRetainsAccessoryAndContainerAttributes() {
        Thumbnail thumbnail = Thumbnail.of("https://example.com/thumb.png", "preview", true);
        TextDisplay[] texts = { TextDisplay.of("a"), TextDisplay.of("b"), TextDisplay.of("c"), TextDisplay.of("d") };
        Container source = Container.builder().accentColor(0x123456).spoiler(true)
                .section(thumbnail, texts).build();
        List<List<PendingComponent>> messages = ComponentPacker.pack(List.of(source));

        assertThat(messages).hasSize(2);
        List<TextDisplay> actual = new ArrayList<>();
        for (List<PendingComponent> message : messages) {
            assertThat(ComponentValidator.isValid(message)).isTrue();
            Container page = (Container) message.getFirst();
            assertThat(page.accentColor()).isEqualTo(0x123456);
            assertThat(page.spoiler()).isTrue();
            Section section = (Section) page.components().getFirst();
            assertThat(section.accessory()).isEqualTo(thumbnail);
            actual.addAll(section.components());
            assertThat(ComponentRenderer.toDiscord4J(message)).hasSize(1);
        }
        assertThat(actual).containsExactly(texts);
    }

    @Test
    void galleryPaginationAccountsForEffectiveCostAtEitherLevel() {
        MediaGalleryItem[] items = new MediaGalleryItem[11];
        for (int i = 0; i < items.length; i++)
            items[i] = MediaGalleryItem.of("https://example.com/" + "a".repeat(980) + i, null, false);

        for (List<PendingComponent> source : List.of(
                List.<PendingComponent>of(MediaGallery.of(items)),
                List.<PendingComponent>of(Container.of(0x123456, true, MediaGallery.of(items))))) {
            List<List<PendingComponent>> messages = ComponentPacker.pack(source);
            assertThat(messages).hasSize(2);
            List<MediaGalleryItem> actual = new ArrayList<>();
            for (List<PendingComponent> message : messages) {
                assertThat(ComponentPacker.fit(message)).isEqualTo(ComponentPacker.Fit.SAFE);
                assertThat(ComponentRenderer.toDiscord4J(message)).hasSize(1);
                PendingComponent component = message.getFirst();
                if (component instanceof Container container)
                    component = container.components().getFirst();
                MediaGallery gallery = (MediaGallery) component;
                assertThat(gallery.items()).hasSizeLessThanOrEqualTo(ComponentLimits.MAX_MEDIA_GALLERY_ITEMS);
                actual.addAll(gallery.items());
            }
            assertThat(actual).containsExactly(items);
        }
    }
}
