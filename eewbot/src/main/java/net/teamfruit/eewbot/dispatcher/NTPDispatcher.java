package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.event.TimeEvent;

public class NTPDispatcher implements Runnable {
	public static final NTPDispatcher INSTANCE = new NTPDispatcher();

	private volatile long offset;

	private NTPDispatcher() {
	}

	public long getOffset() {
		return this.offset;
	}

	public void setOffset(final long millis) {
		this.offset = millis;
	}

	@Override
	public void run() {
		try {
			final TimeInfo info = get();
			info.computeDetails();
			EEWBot.instance.getClient().getDispatcher().dispatch(new TimeEvent(EEWBot.instance.getClient(), info));
			this.offset = getOffset(info);
		} catch (final IOException e) {
			EEWBot.LOGGER.error("NTPClient error", e);
		}
	}

	public static TimeInfo get() throws IOException {
		final NTPUDPClient client = new NTPUDPClient();
		client.setDefaultTimeout(10000);
		client.open();
		final InetAddress hostAddr = InetAddress.getByName(EEWBot.instance.getConfig().getNptServer());
		return client.getTime(hostAddr);
	}

	public static long getOffset(final TimeInfo info) {
		final Long offsetValue = info.getOffset();
		final Long delayValue = info.getDelay();
		return (offsetValue!=null ? offsetValue.longValue() : 0)+(delayValue!=null ? delayValue.longValue() : 0);

	}
}
