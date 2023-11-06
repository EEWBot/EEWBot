package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.http.client.ClientException;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.i18n.I18n;
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
        return event.deferReply()
                .then(bot.getService().sendMessangePassErrors(event.getInteraction().getChannelId().asLong(), this::createMessage)
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
                        .then());
    }

    private MessageCreateSpec createMessage(String lang) {
        return MessageCreateSpec.builder()
                .addEmbed(SlashCommandUtils.createEmbed(lang)
                        .title("eewbot.scmd.testmessage.title")
                        .description("eewbot.scmd.testmessage.desc")
                        .build())
                .build();
    }
}
