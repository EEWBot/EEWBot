package net.teamfruit.eewbot.entity.discord;

import java.util.ArrayList;
import java.util.List;

public class DiscordWebhookRequest {

    private final String lang;
    private final DiscordWebhook webhook;
    private final List<String> targets = new ArrayList<>();

    public DiscordWebhookRequest(String lang, DiscordWebhook webhook) {
        this.lang = lang;
        this.webhook = webhook;
    }

    public String getLang() {
        return this.lang;
    }

    public DiscordWebhook getWebhook() {
        return this.webhook;
    }

    /** Returns an immutable snapshot; use {@link #addTarget(String)} to add normalized destinations. */
    public List<String> getTargets() {
        return List.copyOf(this.targets);
    }

    public DiscordWebhookRequest addTarget(String target) {
        this.targets.add(componentsV2Url(target));
        return this;
    }

    public static String componentsV2Url(final String target) {
        if (target.contains("with_components="))
            return target;
        return target + (target.contains("?") ? "&" : "?") + "with_components=true";
    }

}
