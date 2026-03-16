package net.teamfruit.eewbot.entity.webhooksender;

import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.discord.DiscordWebhookRequest;

import java.util.List;

public record WebhookSenderRequest(List<String> targets, DiscordWebhook body, int retry_limit) {

    public WebhookSenderRequest(List<String> targets, DiscordWebhook body) {
        this(targets, body, 10);
    }

    public static WebhookSenderRequest from(DiscordWebhookRequest request) {
        return new WebhookSenderRequest(request.getTargets(), request.getWebhook());
    }
}
