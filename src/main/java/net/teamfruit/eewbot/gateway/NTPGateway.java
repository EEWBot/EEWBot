package net.teamfruit.eewbot.gateway;

import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import net.teamfruit.eewbot.EEWBot;

public abstract class NTPGateway implements Gateway<TimeInfo> {

	@Override
	public void run() {
		try {
			final NTPUDPClient client = new NTPUDPClient();
			client.setDefaultTimeout(10000);
			client.open();
			final InetAddress hostAddr = InetAddress.getByName(EEWBot.instance.getConfig().getNptServer());
			onNewData(client.getTime(hostAddr));
		} catch (final Exception e) {
			onError(e);
		}
	}
}
