package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.Channel;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

public interface ISlashCommand {

    String getCommandName();

    default boolean isDefer() {
        return false;
    }

    default boolean isEphemeral() {
        return false;
    }

    ApplicationCommandRequest buildCommand();

    Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, @Nullable Channel channel, String lang);
}
