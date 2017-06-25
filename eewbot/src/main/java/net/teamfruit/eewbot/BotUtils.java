package net.teamfruit.eewbot;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

public class BotUtils {

	public static void reply(final MessageReceivedEvent e, final String message) {
		e.getMessage().getChannel().sendMessage(message);
	}
}
