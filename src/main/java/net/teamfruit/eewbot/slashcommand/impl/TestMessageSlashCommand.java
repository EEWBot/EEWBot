package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.WebhookExecuteRequest;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.json.response.ErrorResponse;
import discord4j.rest.request.DiscordWebRequest;
import discord4j.rest.request.Router;
import discord4j.rest.route.Routes;
import discord4j.rest.util.Color;
import discord4j.rest.util.MultipartRequest;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.discord.DiscordWebhookRequest;
import net.teamfruit.eewbot.entity.webhooksender.WebhookSenderRequest;
import net.teamfruit.eewbot.i18n.I18nDiscordEmbed;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import net.teamfruit.eewbot.slashcommand.SlashCommandUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Objects;

public class TestMessageSlashCommand implements ISlashCommand {

    @Override
    public String getCommandName() {
        return "testmessage";
    }

    @Override
    public boolean isDefer() {
        return true;
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("Botからテストメッセージを送信し、現在のチャンネルで正常に動作するか確認します。")
                .build();
    }

    @Override
    public Mono<Void> on(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        boolean hasWebhook = channel != null && channel.getWebhook() != null;

        if (hasWebhook) {
            if (StringUtils.isNotEmpty(ctx.config().getWebhookSender().getAddress())) {
                DiscordWebhook webhook = DiscordWebhook.builder()
                        .addEmbed(I18nDiscordEmbed.builder(lang, ctx.i18n())
                                .title("eewbot.scmd.testmessage.title")
                                .description("eewbot.scmd.testmessage.webhooksender")
                                .color(Color.of(7506394))
                                .author(ctx.username(), "https://github.com/EEWBot/EEWBot", ctx.avatarUrl())
                                .footer("EEWBot/EEWBot", "http://i.imgur.com/gFHBoZA.png")
                                .build())
                        .build();
                DiscordWebhookRequest request = new DiscordWebhookRequest(lang, webhook).addTarget(channel.getWebhook().getUrl());
                try {
                    int statusCode = ctx.service().sendWebhookSenderSingle(WebhookSenderRequest.from(request));
                    if (statusCode >= 200 && statusCode < 300) {
                        return event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.testmessage.success.webhooksender")).then();
                    }
                    return event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknown", "webhook-sender returned abnormal status code: " + statusCode)
                                    .build())
                            .build()).then();
                } catch (IOException | InterruptedException e) {
                    return event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknown", e.getMessage())
                                    .build())
                            .build()).then();
                }
            }
            return executeWebhook(event.getClient().getCoreResources().getRouter(), channel.getWebhook().id(), channel.getWebhook().token(), true, channel.getThreadId(),
                    MultipartRequest.ofRequest(WebhookExecuteRequest.builder()
                            .addEmbed(SlashCommandUtils.createEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.title")
                                    .description("eewbot.scmd.testmessage.webhook")
                                    .build().asRequest())
                            .avatarUrl(ctx.avatarUrl())
                            .build()))
                    .flatMap(message -> event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.testmessage.success.webhook")))
                    .onErrorResume(ClientException.isStatusCode(404), err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknownwebhook", ExceptionUtils.getMessage(err))
                                    .build())
                            .build()))
                    .onErrorResume(ClientException.class, err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknown", err.getErrorResponse().map(ErrorResponse::toString).orElse(""))
                                    .build())
                            .build()))
                    .then();
        } else {
            return ctx.service().directSendMessagePassErrors(channelId, MessageCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.title")
                                    .description("eewbot.scmd.testmessage.nowebhook")
                                    .build())
                            .build())
                    .flatMap(message -> event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.testmessage.success.nowebhook")))
                    .onErrorResume(ClientException.isStatusCode(403), err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.missingperms")
                                    .build())
                            .build()))
                    .onErrorResume(ClientException.isStatusCode(429), err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.ratelimit")
                                    .build())
                            .build()))
                    .onErrorResume(err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang, ctx)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknown", ExceptionUtils.getMessage(err))
                                    .build())
                            .build()))
                    .then();
        }
    }

    // Temporary solution
    public Mono<MessageData> executeWebhook(Router router, long webhookId, String token, boolean wait, Long threadId, MultipartRequest<? extends WebhookExecuteRequest> request) {
        DiscordWebRequest req = Routes.WEBHOOK_EXECUTE.newRequest(webhookId, token)
                .query("wait", wait)
                .header("content-type", request.getFiles().isEmpty() ? "application/json" : "multipart/form-data")
                .body(Objects.requireNonNull(request.getFiles().isEmpty() ? request.getJsonPayload() : request));
        if (threadId != null) {
            req.query("thread_id", threadId);
        }

        return req.exchange(router)
                .bodyToMono(MessageData.class);
    }

}
