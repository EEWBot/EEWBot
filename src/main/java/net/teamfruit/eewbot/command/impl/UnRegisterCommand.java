package net.teamfruit.eewbot.command.impl;

import java.awt.Color;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class UnRegisterCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event) {
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.justOrEmpty(bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createEmbed(embed -> embed.setTitle("チャンネル登録解除")
								.setColor(new Color(255, 64, 64))
								.setDescription("このチャンネルは登録されていません。"))
								.map(m -> false)))
				.flatMap(channel -> Mono.fromCallable(() -> {
					bot.getChannels().remove(channel.getId().asLong());
					bot.getChannelRegistry().save();
					return channel;
				}))
				.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("チャンネル登録解除")
						.setColor(new Color(7506394))
						.setDescription("登録を解除しました。")))
				.then();
	}

}
