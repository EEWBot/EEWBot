package net.teamfruit.eewbot.dispatcher;

import org.apache.commons.net.ntp.TimeInfo;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.Event;

public class TimeEvent extends Event {

	protected final TimeInfo info;

	public TimeEvent(final IDiscordClient client, final TimeInfo info) {
		this.client = client;
		this.info = info;
	}

	public TimeInfo getTimeInfo() {
		return this.info;
	}
}
