package net.teamfruit.eewbot.entity.discord;

import com.google.gson.JsonObject;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.i18n.I18n;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageRefreshButtonTest {

    private final I18n i18n = new I18n("ja_jp");

    @Test
    void addsOneLocalizedButtonToAnImageMessageAndKeepsItsContentAndComponents() {
        ActionRow existing = ActionRow.of(Button.link("https://example.com", "Details"));
        MessageCreateSpec original = MessageCreateSpec.builder().content("notification")
                .addEmbed(EmbedCreateSpec.builder().image("https://example.com/one.png").build())
                .addEmbed(EmbedCreateSpec.builder().image("https://example.com/two.png").build())
                .addComponent(existing).build();

        MessageCreateSpec result = ImageRefreshButton.addTo(original, "en_us", this.i18n);

        assertThat(result.content()).isEqualTo(original.content());
        assertThat(result.embeds()).isEqualTo(original.embeds());
        assertThat(result.componentsOrElse(List.of())).hasSize(2).startsWith(existing);
        Button button = (Button) ((ActionRow) result.componentsOrElse(List.of()).getLast()).getChildren().getFirst();
        assertThat(button.getCustomId()).contains(ImageRefreshButton.CUSTOM_ID);
        assertThat(button.getLabel()).contains("Click here if the image does not appear");
    }

    @Test
    void serializesWebhookButtonAsDiscordComponentsWithoutChangingTheEmbed() {
        DiscordEmbed embed = DiscordEmbed.builder().title("notification").image("https://example.com/map.png").build();
        DiscordWebhook webhook = DiscordWebhook.builder().content("content").addEmbed(embed).build();

        ImageRefreshButton.addTo(webhook, "ja_jp", this.i18n);

        assertThat(webhook.content).isEqualTo("content");
        assertThat(webhook.embeds).containsExactly(embed);
        JsonObject row = Codecs.GSON.toJsonTree(webhook).getAsJsonObject().getAsJsonArray("components").get(0).getAsJsonObject();
        assertThat(row.get("type").getAsInt()).isEqualTo(1);
        JsonObject button = row.getAsJsonArray("components").get(0).getAsJsonObject();
        assertThat(button.get("type").getAsInt()).isEqualTo(2);
        assertThat(button.get("style").getAsInt()).isEqualTo(2);
        assertThat(button.get("custom_id").getAsString()).isEqualTo("eewimage:refresh");
        assertThat(button.get("label").getAsString()).isEqualTo("画像が表示されない場合はこちら");
    }

    @Test
    void leavesTextAndThumbnailOnlyMessagesUnchanged() {
        for (EmbedCreateSpec embed : List.of(EmbedCreateSpec.builder().title("text").build(),
                EmbedCreateSpec.builder().thumbnail("https://example.com/icon.png").build())) {
            MessageCreateSpec original = MessageCreateSpec.builder().addEmbed(embed).build();
            assertThat(ImageRefreshButton.addTo(original, "ja_jp", this.i18n)).isSameAs(original);
        }
        for (DiscordWebhook webhook : List.of(DiscordWebhook.builder().content("text").build(),
                DiscordWebhook.builder().addEmbed(DiscordEmbed.builder().title("text").build()).build())) {
            ImageRefreshButton.addTo(webhook, "ja_jp", this.i18n);
            assertThat(webhook.components).isNull();
        }
    }
}
