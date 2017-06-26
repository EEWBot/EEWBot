package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import net.teamfruit.eewbot.EEWBot;

public class NTPDispatcher implements Runnable {

	private volatile long offset;

	public long getOffset() {
		return this.offset;
	}

	@Override
	public void run() {
		final NTPUDPClient client = new NTPUDPClient();
		client.setDefaultTimeout(10000);
		try {
			client.open();
			final InetAddress hostAddr = InetAddress.getByName(EEWBot.config.nptServer);
			final TimeInfo info = client.getTime(hostAddr);
			EEWBot.client.getDispatcher().dispatch(new TimeEvent(EEWBot.client, info));
			info.computeDetails();
			final Long offsetValue = info.getOffset();
			final Long delayValue = info.getDelay();
			this.offset = (offsetValue!=null ? offsetValue.longValue() : 0)+(delayValue!=null ? delayValue.longValue() : 0);
		} catch (final IOException e) {
			EEWBot.LOGGER.error("NTPClient error", e);
		}
	}
}
