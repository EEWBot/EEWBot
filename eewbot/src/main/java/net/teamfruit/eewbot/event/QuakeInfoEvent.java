package net.teamfruit.eewbot.event;

import net.teamfruit.eewbot.node.QuakeInfo;
import sx.blah.discord.api.IDiscordClient;

public class QuakeInfoEvent extends EEWBotEvent<QuakeInfo> {

	public QuakeInfoEvent(final IDiscordClient client, final QuakeInfo info) {
		super(client, info);
	}

}
