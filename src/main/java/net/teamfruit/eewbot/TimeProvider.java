package net.teamfruit.eewbot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.commons.net.ntp.TimeInfo;

import net.teamfruit.eewbot.gateway.NTPGateway;

public class TimeProvider {

	public static final ZoneId ZONE_ID = ZoneId.of("Asia/Tokyo");

	private long offset;

	private final NTPGateway gateway = new NTPGateway() {

		@Override
		public void onNewData(final TimeInfo data) {
			data.computeDetails();
			final Long offsetValue = data.getOffset();
			final Long delayValue = data.getDelay();
			TimeProvider.this.offset = (offsetValue!=null ? offsetValue.longValue() : 0)+(delayValue!=null ? delayValue.longValue() : 0);

		}
	};

	public long getOffset() {
		return this.offset;
	}

	public NTPGateway getGateway() {
		return this.gateway;
	}

	public ZonedDateTime now() {
		return ZonedDateTime.ofInstant(Instant.now().plusMillis(getOffset()), ZONE_ID);
	}

	public ZonedDateTime offset(final long millis) {
		return ZonedDateTime.ofInstant(Instant.now().plusMillis(getOffset()+millis), ZONE_ID);
	}

}
