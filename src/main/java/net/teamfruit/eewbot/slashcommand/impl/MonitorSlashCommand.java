package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.Monitor;
import net.teamfruit.eewbot.gateway.MonitorGateway;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import reactor.core.publisher.Mono;

public class MonitorSlashCommand implements ISlashCommand {
    @Override
    public String getCommandName() {
        return "monitor";
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("現在の強震モニタを取得します。")
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang) {
        return event.deferReply().then(get(bot, event));
    }

    public Mono<Void> get(EEWBot bot, ApplicationCommandInteractionEvent event) {
        bot.getExecutor().getExecutor().execute(new MonitorGateway(bot.getExecutor().getProvider()) {

            @Override
            public void onNewData(final Monitor data) {
                event.createFollowup().withFiles(data.getFile())
                        .subscribe();
            }
        });
        return Mono.empty();

    }
}
