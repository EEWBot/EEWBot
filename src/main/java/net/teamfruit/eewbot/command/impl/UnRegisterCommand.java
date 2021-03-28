package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class UnRegisterCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.just(bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
								.setTitle("eewbot.cmd.unregister.title")
								.setDescription("eewbot.cmd.err.channelnotregistered.desc"))
								.map(m -> false)))
				.flatMap(channel -> Mono.fromCallable(() -> {
					bot.getChannelsLock().writeLock().lock();
					bot.getChannels().remove(channel.getId().asLong());
					bot.getChannelsLock().writeLock().unlock();
					bot.getChannelRegistry().save();
					return channel;
				}))
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed, lang)
						.setTitle("eewbot.cmd.unregister.title")
						.setDescription("eewbot.cmd.unregister.desc")))
				.then();
	}

}
