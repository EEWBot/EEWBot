package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class AddCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.just(bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
								.setTitle("eewbot.cmd.add.title")
								.setDescription("eewbot.cmd.err.channelnotregistered.desc"))
								.map(m -> false)))
				.filterWhen(channel -> Mono.justOrEmpty(event.getMessage().getContent().map(msg -> msg.split(" ")))
						.filterWhen(array -> Mono.just(array.length>=3)
								.filter(b -> b)
								.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
										.setTitle("eewbot.cmd.add.title")
										.setDescription("eewbot.cmd.err.arg.desc"))
										.map(m -> false)))
						.filterWhen(array -> Mono.just(bot.getChannels().get(channel.getId().asLong()).exits(array[2]))
								.filter(b -> b)
								.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
										.setTitle("eewbot.cmd.add.title")
										.setDescription("eewbot.cmd.err.fieldnotexits"))
										.map(m -> false)))
						.filterWhen(array -> Mono.just(!bot.getChannels().get(channel.getId().asLong()).value(array[2]))
								.filter(b -> b)
								.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
										.setTitle("eewbot.cmd.add.title")
										.setDescription("eewbot.cmd.err.fieldalreadytrue"))
										.map(m -> false)))
						.flatMap(array -> Mono.fromCallable(() -> {
							bot.getChannels().get(channel.getId().asLong()).set(array[2], true);
							bot.getChannelRegistry().save();
							return true;
						})))
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed, lang)
						.setTitle("eewbot.cmd.add.title")
						.setDescription("eewbot.cmd.add.desc")))
				.then();
	}

}
