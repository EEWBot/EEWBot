package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.other.NHKDetailQuakeInfo;
import net.teamfruit.eewbot.entity.other.NHKQuakeInfo;
import net.teamfruit.eewbot.gateway.QuakeInfoGateway;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

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
        if (ctx.config().getLegacy().isEnableLegacyQuakeInfo()) {
            try {
                NHKQuakeInfo info = Codecs.XML_MAPPER.readValue(new URL(QuakeInfoGateway.REMOTE_ROOT + QuakeInfoGateway.REMOTE), NHKQuakeInfo.class);
                Optional<String> url = info.getRecords().stream().findFirst()
                        .flatMap(record -> record.getItems().stream().findFirst())
                        .map(NHKQuakeInfo.Record.Item::getUrl);

                if (url.isEmpty())
                    return event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.error")).then();

                NHKDetailQuakeInfo detail = NHKDetailQuakeInfo.DETAIL_QUAKE_INFO_MAPPER.readValue(new URL(url.get()), NHKDetailQuakeInfo.class);
                return event.createFollowup().withEmbeds(detail.createEmbed(lang, embedCtx, I18nEmbedCreateSpec.builder(lang, embedCtx.i18n()))).then();
            } catch (IOException e) {
                return Mono.error(e);
            }
        }
        return ctx.quakeInfoStore().getLatestReport()
                .map(quakeInfo -> quakeInfo.createEmbed(lang, embedCtx, I18nEmbedCreateSpec.builder(lang, embedCtx.i18n())))
                .map(embed -> event.createFollowup().withEmbeds(embed))
                .orElseGet(() -> event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.quakeinfo.error"))
                        .withEphemeral(true))
                .then();
    }

}
