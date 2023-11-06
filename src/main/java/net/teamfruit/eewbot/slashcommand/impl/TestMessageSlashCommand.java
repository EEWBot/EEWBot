package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandUtils;
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
                .description("テストメッセージを送信します。")
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang) {
        return event.deferReply()
                .then(bot.getService().sendMessange(event.getInteraction().getChannelId().asLong(), this::createMessage)
                        .flatMap(message -> event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.testmessage.success")))
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
