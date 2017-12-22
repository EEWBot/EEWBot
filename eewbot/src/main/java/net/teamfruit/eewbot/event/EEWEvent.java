package net.teamfruit.eewbot.event;

import java.util.Optional;

import net.teamfruit.eewbot.node.EEW;
import sx.blah.discord.api.IDiscordClient;

public class EEWEvent extends EEWBotEvent<EEW> {

	protected final EEW prev;
	protected Optional<byte[]> monitor;

	public EEWEvent(final IDiscordClient client, final EEW eew, final EEW prev) {
		super(client, eew);
		this.prev = prev;
	}

	public EEW getPrev() {
		return this.prev;
	}

	public void setMonitor(final byte[] png) {
		this.monitor = Optional.ofNullable(png);
	}

	public Optional<byte[]> getMonitor() {
		return this.monitor;
	}
}
