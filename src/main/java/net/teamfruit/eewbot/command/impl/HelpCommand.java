package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class HelpCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		return event.getMessage().getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed, lang)
						.setTitle("eewbot.cmd.help.title")
						.addField("register", "eewbot.cmd.help.field.register.value", true)
						.addField("unregister", "eewbot.cmd.help.field.unregister.value", true)
						.addField("details", "eewbot.cmd.help.field.detail.value", true)
						.addField("add", "eewbot.cmd.help.field.add.value", true)
						.addField("remove", "eewbot.cmd.help.field.remove.value", true)
						.addField("monitor", "eewbot.cmd.help.field.monitor.value", true)
						.addField("time", "eewbot.cmd.help.field.time.value", true)
						.addField("timefix", "eewbot.cmd.help.field.timefix.value", true)
						.addField("joinserver", "eewbot.cmd.help.field.joinserver.value", true)
						.addField("reload", "eewbot.cmd.help.field.reload.value", true)
						.addField("help", "eewbot.cmd.help.field.help.value", true)))
				.then();
	}

}
