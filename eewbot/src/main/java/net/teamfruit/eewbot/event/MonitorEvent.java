package net.teamfruit.eewbot.event;

import sx.blah.discord.api.IDiscordClient;

public class MonitorEvent extends EEWBotEvent<byte[]> {

	public MonitorEvent(final IDiscordClient client, final byte[] array) {
		super(client, array);
	}
}
