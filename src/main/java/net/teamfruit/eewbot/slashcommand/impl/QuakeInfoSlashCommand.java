package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
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
    public Mono<Void> on(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        EmbedContext embedCtx = new EmbedContext(ctx.rendererQueryFactory(), ctx.quakeInfoStore(), ctx.i18n());
        return ctx.quakeInfoStore().getLatestReport()
                .map(quakeInfo -> quakeInfo.createEmbed(lang, embedCtx, I18nEmbedCreateSpec.builder(lang, embedCtx.i18n())))
                .map(embed -> event.createFollowup().withEmbeds(embed))
                .orElseGet(() -> event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.quakeinfo.error"))
                        .withEphemeral(true))
                .then();
    }

}
