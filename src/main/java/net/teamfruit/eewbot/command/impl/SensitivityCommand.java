package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import reactor.core.publisher.Mono;

public class SensitivityCommand implements ICommand {

	private SeismicIntensity target;

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.just(bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createMessage(CommandUtils.createErrorEmbed(lang)
								.title("eewbot.cmd.add.title")
								.description("eewbot.cmd.err.channelnotregistered.desc").build())
								.map(m -> false)))
				.filterWhen(channel -> Mono.justOrEmpty(event.getMessage().getContent().split(" "))
						.filterWhen(array -> Mono.just(array.length>=3)
								.filter(b -> b)
								.switchIfEmpty(channel.createMessage(CommandUtils.createErrorEmbed(lang)
										.title("eewbot.cmd.add.title")
										.description("eewbot.cmd.err.arg.desc").build())
										.map(m -> false)))
						.filterWhen(array -> Mono.just(SeismicIntensity.get(array[2]).isPresent())
								.filter(b -> b)
								.switchIfEmpty(channel.createMessage(CommandUtils.createErrorEmbed(lang)
										.title("eewbot.cmd.setlang.title")
										.description("eewbot.cmd.err.fieldnotexits").build())
										.map(m -> false)))
						.flatMap(array -> Mono.fromCallable(() -> {
							this.target = SeismicIntensity.get(array[2]).get();
							bot.getChannels().get(channel.getId().asLong()).minIntensity = this.target;
							bot.getChannelRegistry().save();
							return true;
						})))
				.flatMap(channel -> channel.createMessage(CommandUtils.createEmbed(lang)
						.title("eewbot.cmd.sensitivity.title")
						.description("eewbot.cmd.sensitivity.desc", this.target.getSimple())
						.build()))
				.then();
	}

}
