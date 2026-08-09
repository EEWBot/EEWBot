package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.discord.EmbedPacker;
import net.teamfruit.eewbot.entity.discord.EmbedRenderer;
import net.teamfruit.eewbot.entity.discord.I18nEmbedBuilder;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import reactor.core.publisher.Flux;
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
                .map(quakeInfo -> EmbedPacker.pack(quakeInfo.createEmbeds(lang, embedCtx, () -> new I18nEmbedBuilder(lang, embedCtx.i18n()))))
                // embed が順序どおり届くよう、フォローアップは 1 つずつ順番に送信する
                .map(messages -> Flux.fromIterable(messages)
                        .concatMap(message -> event.createFollowup().withEmbeds(EmbedRenderer.toEmbedCreateSpecs(message)))
                        .then())
                .orElseGet(() -> event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.quakeinfo.error"))
                        .withEphemeral(true)
                        .then());
    }

}
