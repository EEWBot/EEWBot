package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class DetailCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.just(bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createMessage(CommandUtils.createErrorEmbed(lang)
								.title("eewbot.cmd.detail.title")
								.description("eewbot.cmd.err.channelnotregistered.desc").build())
								.map(m -> false)))
				.flatMap(channel -> channel.createMessage(CommandUtils.createEmbed(lang)
						.title("eewbot.cmd.detail.title")
						.description(bot.getChannels().get(channel.getId().asLong()).toString())
						.build()))
				.then();
	}

}
