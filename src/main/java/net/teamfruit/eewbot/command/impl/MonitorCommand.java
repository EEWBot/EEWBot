package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.ICommand;
import net.teamfruit.eewbot.entity.Monitor;
import net.teamfruit.eewbot.gateway.MonitorGateway;
import reactor.core.publisher.Mono;

public class MonitorCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, String lang) {
		bot.getExecutor().getExecutor().execute(new MonitorGateway(bot.getExecutor().getProvider()) {

			@Override
			public void onNewData(final Monitor data) {
				event.getMessage().getChannel()
						.flatMap(channel -> channel.createMessage(data.createMessage()))
						.subscribe();
			}
		});
		return Mono.empty();
	}

}
