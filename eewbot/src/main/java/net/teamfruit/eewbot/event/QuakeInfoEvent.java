package net.teamfruit.eewbot.event;

import net.teamfruit.eewbot.dispatcher.QuakeInfoDispather.UpdateType;
import net.teamfruit.eewbot.node.QuakeInfo;
import sx.blah.discord.api.IDiscordClient;

public class QuakeInfoEvent extends EEWBotEvent<QuakeInfo> {

	private final UpdateType type;
	private final boolean detailUpdate;

	public QuakeInfoEvent(final IDiscordClient client, final QuakeInfo info, final UpdateType type) {
		super(client, info);
		this.type = type;
		this.detailUpdate = type==UpdateType.DETAIL;
	}

	public UpdateType getUpdateType() {
		return this.type;
	}

	@Deprecated
	public boolean isDetailUpdate() {
		return this.detailUpdate;
	}

}
