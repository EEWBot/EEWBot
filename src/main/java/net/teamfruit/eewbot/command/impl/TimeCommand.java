package net.teamfruit.eewbot.command.impl;

import java.time.ZonedDateTime;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class TimeCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		return event.getMessage().getChannel()
				.flatMap(channel -> channel.createMessage(embed -> CommandUtils.createEmbed(lang)
						.title("eewbot.cmd.time.title")
						.addField("eewbot.cmd.time.field.lastpctime.name", bot.getExecutor().getProvider().getLastComputerTime()
								.map(ZonedDateTime::toString)
								.orElse("eewbot.cmd.time.field.nonsync.value"), false)
						.addField("eewbot.cmd.time.field.lastntptime.name", bot.getExecutor().getProvider().getLastNTPTime()
								.map(ZonedDateTime::toString)
								.orElse("eewbot.cmd.time.field.nonsync.value"), false)
						.addField("eewbot.cmd.time.field.nowpctime.name", ZonedDateTime.now(TimeProvider.ZONE_ID).toString(), false)
						.addField("eewbot.cmd.time.field.nowoffsettime.name", bot.getExecutor().getProvider().now().toString(), false)
						.addField("eewbot.cmd.time.field.offset.name", String.valueOf(bot.getExecutor().getProvider().getOffset()), false)))
				.then();
	}

}
