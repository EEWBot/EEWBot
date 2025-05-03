package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.other.NHKDetailQuakeInfo;
import net.teamfruit.eewbot.entity.other.NHKQuakeInfo;
import net.teamfruit.eewbot.gateway.QuakeInfoGateway;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.registry.channel.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
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
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        if (bot.getConfig().isEnableLegacyQuakeInfo()) {
            try {
                NHKQuakeInfo info = EEWBot.XML_MAPPER.readValue(new URL(QuakeInfoGateway.REMOTE_ROOT + QuakeInfoGateway.REMOTE), NHKQuakeInfo.class);
                Optional<String> url = info.getRecords().stream().findFirst()
                        .flatMap(record -> record.getItems().stream().findFirst())
                        .map(NHKQuakeInfo.Record.Item::getUrl);

                if (url.isEmpty())
                    return event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.error")).then();

                NHKDetailQuakeInfo detail = NHKDetailQuakeInfo.DETAIL_QUAKE_INFO_MAPPER.readValue(new URL(url.get()), NHKDetailQuakeInfo.class);
                return event.createFollowup().withEmbeds(detail.createEmbed(lang, I18nEmbedCreateSpec.builder(lang))).then();
            } catch (IOException e) {
                return Mono.error(e);
            }
        }
        return bot.getQuakeInfoStore().getLatestReport()
                .map(quakeInfo -> quakeInfo.createEmbed(lang, I18nEmbedCreateSpec.builder(lang)))
                .map(embed -> event.createFollowup().withEmbeds(embed))
                .orElseGet(() -> event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.quakeinfo.error"))
                        .withEphemeral(true))
                .then();
    }

}
