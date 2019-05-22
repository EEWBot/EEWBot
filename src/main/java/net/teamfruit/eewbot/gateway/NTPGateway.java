package net.teamfruit.eewbot.gateway;

import java.net.InetAddress;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;

public abstract class NTPGateway implements Gateway<TimeInfo> {

	protected long lastTime = -1;

	@Override
	public void run() {
		try {
			final long diff = System.currentTimeMillis()-this.lastTime;
			if (this.lastTime>0&&diff<1500&&diff>-1500)
				return;

			Log.logger.info("NTP Correcting time");

			final NTPUDPClient client = new NTPUDPClient();
			client.setDefaultTimeout(10000);
			client.open();
			final InetAddress hostAddr = InetAddress.getByName(EEWBot.instance.getConfig().getNptServer());

			onNewData(client.getTime(hostAddr));
		} catch (final Exception e) {
			onError(e);
		} finally {
			this.lastTime = System.currentTimeMillis();
		}
	}
}
