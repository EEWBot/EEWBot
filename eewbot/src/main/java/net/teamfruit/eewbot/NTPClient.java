package net.teamfruit.eewbot;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class NTPClient {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(final Runnable r) {
			return new Thread(r, "Thread-NTPClient");
		}
	});

	private volatile long offset;

	private final Runnable ntpclient = new Runnable() {
		@Override
		public void run() {
			final NTPUDPClient client = new NTPUDPClient();
			client.setDefaultTimeout(10000);
			try {
				client.open();
				final InetAddress hostAddr = InetAddress.getByName(EEWBot.config.nptServer);
				final TimeInfo info = client.getTime(hostAddr);
				info.computeDetails();
				final Long offsetValue = info.getOffset();
				final Long delayValue = info.getDelay();
				NTPClient.this.offset = (offsetValue!=null ? offsetValue.longValue() : 0)+(delayValue!=null ? delayValue.longValue() : 0);
			} catch (final IOException e) {
				EEWBot.LOGGER.error("NTPClient error", e);
			}
		}
	};

	public NTPClient() {
		this.executor.scheduleAtFixedRate(this.ntpclient, 0, EEWBot.config.timeFixDelay>=3600 ? EEWBot.config.timeFixDelay : 3600, TimeUnit.SECONDS);
	}

	public long getOffset() {
		return this.offset;
	}

	public void run() {
		this.executor.submit(this.ntpclient);
	}
}
