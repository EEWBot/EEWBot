package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import reactor.core.publisher.Mono;

public class QuakeInfoSlashCommand implements ISlashCommand {
    @Override
    public String getCommandName() {
        return "quakeinfo";
    }

    @Override
    public boolean isDefer() {
        return true;
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("最新の地震情報を取得します。")
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        return bot.getQuakeInfoStore().getLatestReport()
                .map(quakeInfo -> quakeInfo.createEmbed(lang, I18nEmbedCreateSpec.builder(lang)))
                .map(embed -> event.createFollowup().withEmbeds(embed))
                .orElseGet(() -> event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.quakeinfo.error"))
                        .withEphemeral(true))
                .then();
    }

}
