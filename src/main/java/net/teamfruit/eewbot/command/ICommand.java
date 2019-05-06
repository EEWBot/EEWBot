package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public interface ICommand {

	Mono<Message> execute(EEWBot bot, MessageCreateEvent event, String[] args);

}
