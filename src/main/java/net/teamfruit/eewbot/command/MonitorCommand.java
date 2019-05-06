package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.Monitor;
import net.teamfruit.eewbot.gateway.MonitorGateway;
import reactor.core.publisher.Mono;

public class MonitorCommand implements ICommand {

	@Override
	public Mono<Message> execute(final EEWBot bot, final MessageCreateEvent event, final String[] args) {
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
