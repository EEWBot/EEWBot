package net.teamfruit.eewbot.event;

import java.io.InputStream;

import sx.blah.discord.api.IDiscordClient;

public class MonitorEvent extends EEWBotEvent<InputStream> {

	public MonitorEvent(final IDiscordClient client, final InputStream is) {
		super(client, is);
	}
}
