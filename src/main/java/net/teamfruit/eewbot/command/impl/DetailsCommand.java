package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class DetailsCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, String lang) {
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.just(bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed)
								.setTitle("チャンネル設定")
								.setDescription("このチャンネルは登録されていません。"))
								.map(m -> false)))
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed)
						.setTitle("チャンネル設定")
						.setDescription(bot.getChannels().get(channel.getId().asLong()).toString())))
				.then();
	}

}
