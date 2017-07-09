package net.teamfruit.eewbot.dispatcher;

import net.teamfruit.eewbot.node.QuakeInfo;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.Event;

public class QuakeInfoEvent extends Event {

	protected final QuakeInfo info;

	public QuakeInfoEvent(final IDiscordClient client, final QuakeInfo info) {
		this.client = client;
		this.info = info;
	}

	public QuakeInfo getQuakeInfo() {
		return this.info;
	}
}
