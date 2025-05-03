package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.net.InetAddress;

public abstract class NTPGateway implements Gateway<TimeInfo> {

    protected long lastTime = -1;

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-ntp-thread");

            final long diff = System.currentTimeMillis() - this.lastTime;
            if (this.lastTime > 0 && diff < 1500 && diff > -1500)
                return;

            Log.logger.info("NTP Correcting time");

            final NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(10000);
            client.open();
            final InetAddress hostAddr = InetAddress.getByName(EEWBot.instance.getConfig().getLegacy().getNtpServer());

            onNewData(client.getTime(hostAddr));
        } catch (final Exception e) {
            onError(new EEWGatewayException("Failed to fetch NTP", e));
        } finally {
            this.lastTime = System.currentTimeMillis();
        }
    }
}
