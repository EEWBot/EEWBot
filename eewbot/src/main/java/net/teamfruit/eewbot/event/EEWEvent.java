package net.teamfruit.eewbot.event;

import net.teamfruit.eewbot.node.EEW;
import sx.blah.discord.api.IDiscordClient;

public class EEWEvent extends EEWBotEvent<EEW> {

	public EEWEvent(final IDiscordClient client, final EEW eew) {
		super(client, eew);
	}

}
