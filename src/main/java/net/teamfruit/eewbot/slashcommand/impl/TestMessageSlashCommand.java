package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.WebhookExecuteRequest;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.MultipartRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.core.publisher.Mono;

public class TestMessageSlashCommand implements ISlashCommand {

    @Override
    public String getCommandName() {
        return "testmessage";
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("Botからテストメッセージを送信し、現在のチャンネルで正常に動作するか確認します。")
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        Channel channel = bot.getChannels().get(channelId);
        boolean hasWebhook = channel != null && channel.webhook != null;

        if (hasWebhook) {
            return event.deferReply()
                    .then(event.getClient().getRestClient().getWebhookService()
                            .executeWebhook(Long.parseLong(channel.webhook.id),
                                    channel.webhook.threadId != null ? channel.webhook.token + "?thread_id=" + channel.webhook.threadId : channel.webhook.token,
                                    true,
                                    MultipartRequest.ofRequest(WebhookExecuteRequest.builder()
                                            .addEmbed(SlashCommandUtils.createEmbed(lang)
                                                    .title("eewbot.scmd.testmessage.title")
                                                    .description("eewbot.scmd.testmessage.webhook")
                                                    .build().asRequest())
                                            .build())))
                    .flatMap(message -> event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.testmessage.success")))
                    .onErrorResume(ClientException.isStatusCode(404), err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknownwebhook", ExceptionUtils.getMessage(err))
                                    .build())
                            .build()))
                    .onErrorResume(err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknown", ExceptionUtils.getMessage(err))
                                    .build())
                            .build()))
                    .then();
        } else {
            return event.deferReply()
                    .then(bot.getService().sendMessagePassErrors(channelId, MessageCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createEmbed(lang)
                                    .title("eewbot.scmd.testmessage.title")
                                    .description("eewbot.scmd.testmessage.normal")
                                    .build())
                            .build()))
                    .flatMap(message -> event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.testmessage.success")))
                    .onErrorResume(ClientException.isStatusCode(403), err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.missingperms")
                                    .build())
                            .build()))
                    .onErrorResume(ClientException.isStatusCode(429), err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.ratelimit")
                                    .build())
                            .build()))
                    .onErrorResume(err -> event.createFollowup(InteractionFollowupCreateSpec.builder()
                            .addEmbed(SlashCommandUtils.createErrorEmbed(lang)
                                    .title("eewbot.scmd.testmessage.error.title")
                                    .description("eewbot.scmd.testmessage.error.unknown", ExceptionUtils.getMessage(err))
                                    .build())
                            .build()))
                    .then();
        }
    }

}
