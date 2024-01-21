package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandUtils;
import reactor.core.publisher.Mono;

public class HelpSlashCommand implements ISlashCommand {
    @Override
    public String getCommandName() {
        return "help";
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("Helpを表示します。")
                .build();

    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        return event.reply().withEmbeds(SlashCommandUtils.createEmbed(lang)
                .title("eewbot.scmd.help.title")
                .description("eewbot.scmd.help.desc")
                .addField("/setup", "eewbot.scmd.help.field.set.value", false)
                .addField("/quakeinfo", "eewbot.scmd.help.field.quakeinfo.value", false)
                .addField("/time", "eewbot.scmd.help.field.time.value", false)
                .addField("/invite", "eewbot.scmd.help.field.invite.value", false)
                .addField("/testmessage", "eewbot.scmd.help.field.testmessage.value", false)
                .addField("/lang", "eewbot.scmd.help.field.lang.value", false)
                .addField("/help", "eewbot.scmd.help.field.help.value", false)
                .addField("eewbot.scmd.help.field.links.name", "eewbot.scmd.help.field.links.value", false)
                .build());
    }
}
