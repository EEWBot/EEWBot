package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import reactor.core.publisher.Mono;

public class SensitivityCommand implements ICommand {

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
						.filterWhen(array -> Mono.just(array.length>=2)
								.filter(b -> b)
								.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
										.setTitle("eewbot.cmd.add.title")
										.setDescription("eewbot.cmd.err.arg.desc"))
										.map(m -> false)))
						.filterWhen(array -> Mono.just(SeismicIntensity.get(array[2]).isPresent())
								.filter(b -> b)
								.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
										.setTitle("eewbot.cmd.setlang.title")
										.setDescription("eewbot.cmd.err.fieldnotexits"))
										.map(m -> false)))
						.flatMap(array -> Mono.fromCallable(() -> {
							final SeismicIntensity intensity = SeismicIntensity.get(array[2]).get();
							bot.getChannels().get(channel.getId().asLong()).minIntensity = intensity;
							bot.getChannelRegistry().save();
							return true;
						})))
				.then();
	}

}
