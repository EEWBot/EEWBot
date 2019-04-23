package net.teamfruit.eewbot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.ntp.TimeInfo;

import net.teamfruit.eewbot.gateway.NTPGateway;
import reactor.core.publisher.Mono;

public class TimeProvider {

	public static final ZoneId ZONE_ID = ZoneId.of("Asia/Tokyo");

	private long offset;
	private Optional<ZonedDateTime> lastComputerTime;
	private Optional<ZonedDateTime> lastNTPTime;

	private final NTPGateway gateway = new Gateway();
	private final ScheduledExecutorService executor;

	public TimeProvider(final ScheduledExecutorService executor) {
		this.executor = executor;
	}

	public Optional<ZonedDateTime> getLastComputerTime() {
		return this.lastComputerTime;
	}

	public Optional<ZonedDateTime> getLastNTPTime() {
		return this.lastNTPTime;
	}

	public void init(final long period) {
		this.executor.scheduleAtFixedRate(this.gateway, 0, period, TimeUnit.SECONDS);
	}

	public Mono<TimeProvider> fetch() {
		return Mono.create(e -> this.executor.execute(new Gateway() {
			@Override
			public void onNewData(final TimeInfo data) {
				super.onNewData(data);
				e.success(TimeProvider.this);
			}

			@Override
			public void onError(final Exception exception) {
				super.onError(exception);
				e.error(exception);
			}
		}));
	}

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

	public class Gateway extends NTPGateway {

		@Override
		public void onNewData(final TimeInfo data) {
			data.computeDetails();
			final Long offsetValue = data.getOffset();
			final Long delayValue = data.getDelay();
			TimeProvider.this.offset = (offsetValue!=null ? offsetValue.longValue() : 0)+(delayValue!=null ? delayValue.longValue() : 0);
			TimeProvider.this.lastComputerTime = Optional.of(ZonedDateTime.now(ZONE_ID));
			TimeProvider.this.lastNTPTime = Optional.of(now());
		}

	}

}
