package net.teamfruit.eewbot.dispatcher;

import net.teamfruit.eewbot.node.EEW;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.Event;

public class EEWEvent extends Event {

	protected final EEW eew;

	public EEWEvent(final IDiscordClient client, final EEW eew) {
		this.client = client;
		this.eew = eew;
	}

	public EEW getEEW() {
		return this.eew;
	}
}
