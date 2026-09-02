package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
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
    public Mono<Void> on(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        return event.reply().withComponents(SlashCommandUtils.render(SlashCommandUtils.createComponent(lang, ctx)
                .heading("eewbot.scmd.help.title")
                .text("eewbot.scmd.help.desc")
                .detail("/setup", "eewbot.scmd.help.field.set.value")
                .detail("/quakeinfo", "eewbot.scmd.help.field.quakeinfo.value")
                .detail("/invite", "eewbot.scmd.help.field.invite.value")
                .detail("/testmessage", "eewbot.scmd.help.field.testmessage.value")
                .detail("/lang", "eewbot.scmd.help.field.lang.value")
                .detail("/unregister", "eewbot.scmd.help.field.unregister.value")
                .detail("/help", "eewbot.scmd.help.field.help.value")
                .separator()
                .detail("eewbot.scmd.help.field.links.name", "eewbot.scmd.help.field.links.value")
                .detail("eewbot.scmd.help.field.legal.name", "eewbot.scmd.help.field.legal.value")));
    }
}
