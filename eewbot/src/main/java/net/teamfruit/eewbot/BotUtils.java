package net.teamfruit.eewbot;

import java.util.Collection;
import java.util.Iterator;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

public class BotUtils {

	public static void reply(final MessageReceivedEvent e, final String message) {
		e.getMessage().getChannel().sendMessage(message);
	}

	public static Channel getChannel(final long serverId, final long channelId) {
		final Collection<Channel> channels = EEWBot.channels.get(serverId);
		if (channels!=null)
			for (final Iterator<Channel> it = channels.iterator(); it.hasNext();) {
				final Channel c = it.next();
				if (c.getId()==channelId)
					return c;
			}
		return null;
	}

}
