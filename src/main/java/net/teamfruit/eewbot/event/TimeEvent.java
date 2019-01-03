package net.teamfruit.eewbot.event;

import org.apache.commons.net.ntp.TimeInfo;

import sx.blah.discord.api.IDiscordClient;

public class TimeEvent extends EEWBotEvent<TimeInfo> {

	public TimeEvent(final IDiscordClient client, final TimeInfo info) {
		super(client, info);
	}

}
