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

    public List<String> getTargets() {
        return this.targets;
    }

    public DiscordWebhookRequest addTarget(String target) {
        this.targets.add(target);
        return this;
    }

}
