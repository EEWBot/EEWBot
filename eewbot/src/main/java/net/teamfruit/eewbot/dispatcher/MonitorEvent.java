package net.teamfruit.eewbot.dispatcher;

import java.io.InputStream;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.Event;

public class MonitorEvent extends Event {

	protected InputStream is;

	public MonitorEvent(final IDiscordClient client, final InputStream is) {
		this.client = client;
		this.is = is;
	}

	public InputStream getImage() {
		return this.is;
	}
}
