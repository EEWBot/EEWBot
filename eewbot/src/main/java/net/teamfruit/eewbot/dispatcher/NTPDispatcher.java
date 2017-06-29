package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import net.teamfruit.eewbot.EEWBot;

public class NTPDispatcher implements Runnable {
	public static final NTPDispatcher INSTANCE = new NTPDispatcher();

	private volatile long offset;

	private NTPDispatcher() {
	}

	public long getOffset() {
		return this.offset;
	}

	@Override
	public void run() {
		try {
			final TimeInfo info = get();
			info.computeDetails();
			EEWBot.instance.getClient().getDispatcher().dispatch(new TimeEvent(EEWBot.instance.getClient(), info));
			setOffset(info);
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

	public void setOffset(final TimeInfo info) {
		final Long offsetValue = info.getOffset();
		final Long delayValue = info.getDelay();
		this.offset = (offsetValue!=null ? offsetValue.longValue() : 0)+(delayValue!=null ? delayValue.longValue() : 0);

	}
}
