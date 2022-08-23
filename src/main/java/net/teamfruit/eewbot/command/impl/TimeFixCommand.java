package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.spec.MessageEditSpec;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;

public class TimeFixCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		return Mono.zip(event.getMessage().getChannel()
				.flatMap(channel -> channel.createMessage(CommandUtils.createEmbed(lang)
						.title("eewbot.cmd.timefix.title")
						.description("eewbot.cmd.timefix.desc").build())),
				bot.getExecutor().getProvider().fetch())
				.flatMap(tuple -> tuple.getT1().edit(MessageEditSpec.builder().addEmbed(CommandUtils.createEmbed(lang)
						.title("eewbot.cmd.timefix.title")
						.addField("eewbot.cmd.timefix.field.nowpctime.name", ZonedDateTime.now(TimeProvider.ZONE_ID).toString(), false)
						.addField("eewbot.cmd.timefix.field.nowoffsettime.name", bot.getExecutor().getProvider().now().toString(), false)
						.addField("eewbot.cmd.timefix.field.offset.name", String.valueOf(bot.getExecutor().getProvider().getOffset()), false)
						.build()).build()))
				.then();
	}

}
