package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public interface ISlashCommand {

	ApplicationCommandRequest command();

	Mono<?> execute(EEWBot bot, InteractionCreateEvent event) throws Exception;
}
