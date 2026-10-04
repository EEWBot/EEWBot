package net.teamfruit.eewbot.entity.discord;

import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRendererTest {

    @Test
    void rendersOptionalDescriptionsAndExplicitSeparatorOptions() {
        PendingComponent.Container source = PendingComponent.Container.builder()
                .section(PendingComponent.Thumbnail.of("https://example.com/thumb.png", null, true),
                        PendingComponent.TextDisplay.of("Section"))
                .separator(false, PendingComponent.Spacing.LARGE)
                .mediaGallery(PendingComponent.MediaGalleryItem.of("https://example.com/map.png", null, true))
                .build();

        var rendered = ComponentRenderer.toDiscord4J(List.of(source)).getFirst().getData().components().get();
        assertThat(rendered.get(0).accessory().get().description().isAbsent()).isTrue();
        assertThat(rendered.get(0).accessory().get().spoiler().get()).isTrue();
        assertThat(rendered.get(1).divider().get()).isFalse();
        assertThat(rendered.get(1).spacing().get()).isEqualTo(2);
        assertThat(rendered.get(2).items().get().getFirst().description().isAbsent()).isTrue();
        assertThat(rendered.get(2).items().get().getFirst().spoiler().get()).isTrue();
    }

    @Test
    void rendersExpectedWebhookShape() {
        PendingComponent.Container source = new PendingComponent.Container(List.of(
                new PendingComponent.TextDisplay("# 地震情報"),
                new PendingComponent.Separator(true, PendingComponent.Spacing.LARGE),
                new PendingComponent.MediaGallery(List.of(new PendingComponent.MediaGalleryItem(
                        "https://example.com/map.png", "震度分布", false)))
        ), 0xff4040, false);

        DiscordComponent.Container result = (DiscordComponent.Container) ComponentRenderer.toWebhook(List.of(source)).getFirst();
        assertThat(result.type()).isEqualTo(17);
        assertThat(result.accentColor()).isEqualTo(0xff4040);
        assertThat(result.components()).extracting("type").containsExactly(10, 14, 12);
    }

    @Test
    void discord4jAutomaticallySetsComponentsV2Flag() {
        PendingComponent.Container source = new PendingComponent.Container(
                List.of(new PendingComponent.TextDisplay("test")), null, false);
        MessageCreateSpec spec = MessageCreateSpec.builder()
                .addAllComponents(ComponentRenderer.toDiscord4J(List.of(source))).build();

        int flags = spec.asRequest().getJsonPayload().flags().get();
        assertThat(flags & Message.Flag.IS_COMPONENTS_V2.getFlag()).isNotZero();
        assertThat(spec.asRequest().getJsonPayload().content().isAbsent()).isTrue();
        assertThat(spec.asRequest().getJsonPayload().embeds().isAbsent()).isTrue();
    }

    @Test
    void webhookContainsFlagAndNoLegacyMessageFields() {
        DiscordWebhook webhook = DiscordWebhook.builder().components(List.of(
                new DiscordComponent.TextDisplay(10, "test"))).build();
        String json = net.teamfruit.eewbot.Codecs.GSON.toJson(webhook);
        com.google.gson.JsonObject object = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertThat(json).contains("\"flags\":32768", "\"components\"")
                .doesNotContain("\"embeds\"");
        assertThat(object.has("content")).isFalse();
        assertThat(object.has("embeds")).isFalse();
    }
}
