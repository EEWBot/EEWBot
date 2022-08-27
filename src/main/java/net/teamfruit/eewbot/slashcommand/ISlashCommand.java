package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

public interface ISlashCommand {

    String getCommandName();

    ApplicationCommandRequest buildCommand();

    Mono on(ApplicationCommandInteractionEvent event);
}
