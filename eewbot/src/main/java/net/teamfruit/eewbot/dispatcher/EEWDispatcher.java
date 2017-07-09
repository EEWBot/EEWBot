package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.FastDateFormat;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.node.EEW;

public class EEWDispatcher implements Runnable {

	public static final EEWDispatcher INSTANCE = new EEWDispatcher();

	public static final String REMOTE = "http://www.kmoni.bosai.go.jp/new/webservice/hypo/eew/";
	public static final FastDateFormat FORMAT = FastDateFormat.getInstance("yyyyMMddHHmmss");

	private final Map<Long, Integer> prev = new HashMap<>();

	private EEWDispatcher() {
	}

	@Override
	public void run() {
		final Date date = new Date(System.currentTimeMillis()+NTPDispatcher.INSTANCE.getOffset()-TimeUnit.SECONDS.toMillis(1));
		final String url = REMOTE+FORMAT.format(date)+".json";
		try {
			final EEW res = get(url);
			if (res!=null&&res.isEEW()) {
				final Integer latestReport = this.prev.get(res.getReportId());
				if (latestReport==null||latestReport<res.getReportNum()) {
					this.prev.put(res.getReportId(), res.getReportNum());
					EEWBot.instance.getClient().getDispatcher().dispatch(new EEWEvent(EEWBot.instance.getClient(), res));
				}
			} else
				this.prev.clear();
		} catch (final IOException e) {
			EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static EEW get(final String url) throws IOException {
		EEWBot.LOGGER.debug("Remote: "+url);
		try (InputStreamReader isr = new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8)) {
			final EEW res = EEWBot.GSON.fromJson(isr, EEW.class);
			EEWBot.LOGGER.debug(res.toString());
			return res;
		}
	}
}
