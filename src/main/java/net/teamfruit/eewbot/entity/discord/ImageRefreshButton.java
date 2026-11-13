package net.teamfruit.eewbot.entity.discord;

import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.TopLevelMessageComponent;
import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adds a recovery button to image-bearing notification messages at delivery time. */
public final class ImageRefreshButton {

    public static final String CUSTOM_ID = "eewimage:refresh";
    private static final String LABEL_KEY = "eewbot.scmd.image.refresh.label";

    private ImageRefreshButton() {
    }

    public static MessageCreateSpec addTo(MessageCreateSpec message, String lang, I18n i18n) {
        if (message.embedsOrElse(List.of()).stream().noneMatch(embed -> embed.imageOrElse(null) != null))
            return message;
        List<TopLevelMessageComponent> components = new ArrayList<>(message.componentsOrElse(List.of()));
        components.add(ActionRow.of(Button.secondary(CUSTOM_ID, i18n.get(lang, LABEL_KEY))));
        return message.withComponents(components);
    }

    public static void addTo(DiscordWebhook webhook, String lang, I18n i18n) {
        if (webhook.embeds == null || webhook.embeds.stream().noneMatch(embed -> embed.image != null && embed.image.url != null))
            return;
        if (webhook.components == null)
            webhook.components = new ArrayList<>();
        webhook.components.add(Map.of("type", 1, "components", List.of(Map.of(
                "type", 2, "style", 2, "custom_id", CUSTOM_ID, "label", i18n.get(lang, LABEL_KEY)))));
    }
}
