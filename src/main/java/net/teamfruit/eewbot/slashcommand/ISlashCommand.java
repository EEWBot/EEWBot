package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public interface ISlashCommand {

    String getCommandName();

    default boolean isDefer() {
        return false;
    }

    ApplicationCommandRequest buildCommand();

    Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang);
}
