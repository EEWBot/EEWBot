package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.DetailQuakeInfo;
import net.teamfruit.eewbot.entity.QuakeInfo;
import net.teamfruit.eewbot.gateway.QuakeInfoGateway;
import net.teamfruit.eewbot.i18n.I18n;
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
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("最新の地震情報を取得します。")
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang) {
        return event.deferReply().then(get(event, lang)).then();
    }

    private Mono<Message> get(ApplicationCommandInteractionEvent event, String lang) {
        try {
            QuakeInfo info = QuakeInfo.QUAKE_INFO_MAPPER.readValue(new URL(QuakeInfoGateway.REMOTE_ROOT + QuakeInfoGateway.REMOTE), QuakeInfo.class);
            Optional<String> url = info.getRecords().stream().findFirst()
                    .flatMap(record -> record.getItems().stream().findFirst())
                    .map(QuakeInfo.Record.Item::getUrl);

            if (!url.isPresent())
                return event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.error"));

            DetailQuakeInfo detail = DetailQuakeInfo.DETAIL_QUAKE_INFO_MAPPER.readValue(new URL(url.get()), DetailQuakeInfo.class);
            return event.createFollowup().withEmbeds(detail.createEmbed(lang));
        } catch (IOException e) {
            return Mono.error(e);
        }

    }
}
