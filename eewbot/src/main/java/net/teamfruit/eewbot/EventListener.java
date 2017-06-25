package net.teamfruit.eewbot;

import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.ChannelDeleteEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

public class EventListener {

	@EventSubscriber
	public void onMessageReceived(final MessageReceivedEvent e) {
		final String msg = e.getMessage().getContent();
		if (msg.startsWith("!eew")) {
			final String[] args = msg.split(" ");
			if (args.length<=1)
				BotUtils.reply(e, "引数が不足しています！");
			else {
				final Command command = EnumUtils.getEnum(Command.class, args[1]);
				if (command!=null)
					command.onCommand(e, ArrayUtils.subarray(args, 2, args.length+1));
			}
		}
	}

	@EventSubscriber
	public void onChannelDelete(final ChannelDeleteEvent e) {
		final Collection<Channel> channels = EEWBot.channels.get(e.getChannel().getGuild().getLongID());
		if (channels!=null) {
			final long id = e.getChannel().getLongID();
			for (final Iterator<Channel> it = channels.iterator(); it.hasNext();)
				if (it.next().id==id)
					it.remove();
			try {
				EEWBot.saveConfigs();
			} catch (final ConfigException ex) {
				EEWBot.LOGGER.error("Error on channel delete", ex);
			}
		}
	}

	public static enum Command {
		register {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {

			}
		},
		unregister {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {

			}
		};

		public abstract void onCommand(MessageReceivedEvent e, String[] args);
	}
}
