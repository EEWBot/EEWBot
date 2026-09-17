package net.teamfruit.eewbot.entity.discord;

import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRendererTest {

    @Test
    void rendersExpectedWebhookShape() {
        PendingComponent.Container source = new PendingComponent.Container(List.of(
                new PendingComponent.Text("# 地震情報"),
                new PendingComponent.Separator(true, PendingComponent.Spacing.LARGE),
                new PendingComponent.MediaGallery(List.of(new PendingComponent.MediaItem(
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
                List.of(new PendingComponent.Text("test")), null, false);
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
