package net.teamfruit.eewbot.command;

import java.awt.Color;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public class DetailsCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event) {
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.justOrEmpty(bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createEmbed(embed -> embed.setTitle("チャンネル設定")
								.setColor(new Color(255, 64, 64))
								.setDescription("このチャンネルは登録されていません。"))
								.map(m -> false)))
				.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("チャンネル設定")
						.setColor(new Color(7506394))
						.setDescription(bot.getChannels().get(channel.getId().asLong()).toString())))
				.then();
	}

}
