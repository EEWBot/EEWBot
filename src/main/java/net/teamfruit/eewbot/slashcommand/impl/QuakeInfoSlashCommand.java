package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.entity.ComponentContext;
import net.teamfruit.eewbot.entity.discord.ComponentPacker;
import net.teamfruit.eewbot.entity.discord.ComponentRenderer;
import net.teamfruit.eewbot.entity.discord.I18nComponentBuilder;
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
        ComponentContext componentCtx = new ComponentContext(ctx.rendererQueryFactory(), ctx.quakeInfoStore(), ctx.i18n());
        return ctx.quakeInfoStore().getLatestReport()
                .map(quakeInfo -> ComponentPacker.pack(quakeInfo.createComponents(lang, componentCtx,
                        () -> new I18nComponentBuilder(lang, componentCtx.i18n()))))
                // components が順序どおり届くよう、フォローアップは 1 つずつ順番に送信する
                .map(messages -> Flux.fromIterable(messages)
                        .concatMap(message -> event.createFollowup().withComponents(ComponentRenderer.toDiscord4J(message)))
                        .then())
                .orElseGet(() -> event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.quakeinfo.error"))
                        .withEphemeral(true)
                        .then());
    }

}
