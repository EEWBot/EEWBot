package net.teamfruit.eewbot;

import java.util.Collection;
import java.util.Iterator;

public class Channel {
	private final long id;
	public boolean eewAlart;
	public boolean eewPrediction;

	public Channel(final long id) {
		this.id = id;
	}

	public long getId() {
		return this.id;
	}

	public static Channel getChannel(final long serverId, final long channelId) {
		final Collection<Channel> channels = EEWBot.channels.get(serverId);
		if (channels!=null)
			for (final Iterator<Channel> it = channels.iterator(); it.hasNext();) {
				final Channel c = it.next();
				if (c.getId()==channelId)
					return c;
			}
		return null;
	}
}
