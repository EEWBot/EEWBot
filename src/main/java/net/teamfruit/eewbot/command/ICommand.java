package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public interface ICommand {

	Mono<Void> execute(EEWBot bot, MessageCreateEvent event);

}
