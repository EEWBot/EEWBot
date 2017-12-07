package net.teamfruit.eewbot.event;

import net.teamfruit.eewbot.node.EEW;
import sx.blah.discord.api.IDiscordClient;

public class EEWEvent extends EEWBotEvent<EEW> {

	protected final EEW prev;

	public EEWEvent(final IDiscordClient client, final EEW eew, final EEW prev) {
		super(client, eew);
		this.prev = prev;
	}

	public EEW getPrev() {
		return this.prev;
	}

}
