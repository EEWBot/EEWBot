package net.teamfruit.eewbot.command;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReactionWaitingList implements Runnable {

	private final Map<ReactionCommand, Long> map = new ConcurrentHashMap<>();

	public ReactionWaitingList(final ScheduledExecutorService executor) {
		executor.scheduleAtFixedRate(this, 1, 1, TimeUnit.MINUTES);
	}

	public long add(final ReactionCommand cmd) {
		return this.map.put(cmd, System.currentTimeMillis());
	}

	@Override
	public void run() {
		final long now = System.currentTimeMillis();
		final long fiveMinutesAgo = now-TimeUnit.MINUTES.toMillis(5);
		for (final Iterator<Entry<ReactionCommand, Long>> iterator = this.map.entrySet().iterator(); iterator.hasNext();) {
			final Entry<ReactionCommand, Long> entry = iterator.next();
			if (entry.getValue()<=fiveMinutesAgo)
				iterator.remove();
		}
	}
}