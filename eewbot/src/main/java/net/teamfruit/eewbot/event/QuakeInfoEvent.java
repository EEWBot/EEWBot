package net.teamfruit.eewbot.event;

import net.teamfruit.eewbot.node.QuakeInfo;
import sx.blah.discord.api.IDiscordClient;

public class QuakeInfoEvent extends EEWBotEvent<QuakeInfo> {

	private final boolean detailUpdate;

	public QuakeInfoEvent(final IDiscordClient client, final QuakeInfo info, final boolean detailUpdate) {
		super(client, info);
		this.detailUpdate = detailUpdate;
	}

	public boolean isDetailUpdate() {
		return this.detailUpdate;
	}

}
