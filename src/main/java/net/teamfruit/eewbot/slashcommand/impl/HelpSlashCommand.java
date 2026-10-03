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
        return event.reply().withComponents(SlashCommandUtils.render(SlashCommandUtils.createContainer()
                .textDisplay("# " + ctx.i18n().get(lang, "eewbot.scmd.help.title"))
                .textDisplay(ctx.i18n().get(lang, "eewbot.scmd.help.desc"))
                .textDisplay("**" + ctx.i18n().get(lang, "/setup") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.set.value"))
                .textDisplay("**" + ctx.i18n().get(lang, "/quakeinfo") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.quakeinfo.value"))
                .textDisplay("**" + ctx.i18n().get(lang, "/invite") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.invite.value"))
                .textDisplay("**" + ctx.i18n().get(lang, "/testmessage") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.testmessage.value"))
                .textDisplay("**" + ctx.i18n().get(lang, "/lang") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.lang.value"))
                .textDisplay("**" + ctx.i18n().get(lang, "/unregister") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.unregister.value"))
                .textDisplay("**" + ctx.i18n().get(lang, "/help") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.help.value"))
                .separator()
                .textDisplay("**" + ctx.i18n().get(lang, "eewbot.scmd.help.field.links.name") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.links.value"))
                .textDisplay("**" + ctx.i18n().get(lang, "eewbot.scmd.help.field.legal.name") + "**\n" + ctx.i18n().get(lang, "eewbot.scmd.help.field.legal.value"))
                .textDisplay("-# EEWBot/EEWBot")));
    }
}
