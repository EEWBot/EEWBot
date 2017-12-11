package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.event.EEWEvent;
import net.teamfruit.eewbot.node.EEW;

public class EEWDispatcher implements Runnable {

	public static final EEWDispatcher INSTANCE = new EEWDispatcher();

	public static final String REMOTE = "http://www.kmoni.bosai.go.jp/new/webservice/hypo/eew/";
	public static final FastDateFormat FORMAT = FastDateFormat.getInstance("yyyyMMddHHmmss");

	private final Map<Long, EEW> prev = new HashMap<>();

	private EEWDispatcher() {
	}

	@Override
	public void run() {
		final Date date = new Date(System.currentTimeMillis()+NTPDispatcher.INSTANCE.getOffset()-TimeUnit.SECONDS.toMillis(1));
		final String url = REMOTE+FORMAT.format(date)+".json";
		try {
			final EEW res = get(url);
			if (res!=null&&res.isEEW()) {
				final EEW last = this.prev.get(res.getReportId());
				if (last==null||last.getReportNum()<res.getReportNum()) {
					EEWBot.instance.getClient().getDispatcher().dispatch(new EEWEvent(EEWBot.instance.getClient(), res, res.isInitial() ? null : last));
					this.prev.put(res.getReportId(), res);
					if (res.isInitial()||res.isFinal())
						EEWBot.instance.getExecutor().execute(MonitorDispatcher.INSTANCE);
				}
			} else
				this.prev.clear();
		} catch (final IOException e) {
			Log.logger.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static EEW get(final String url) throws IOException {
		Log.logger.debug("Remote: "+url);
		final HttpGet get = new HttpGet(url);
		final HttpResponse response = EEWBot.instance.getHttpClient().execute(get);
		if (response.getStatusLine().getStatusCode()==HttpStatus.SC_OK)
			try (InputStreamReader is = new InputStreamReader(response.getEntity().getContent())) {
				final EEW res = EEWBot.GSON.fromJson(is, EEW.class);
				Log.logger.debug(res.toString());
				return res;
			}
		return null;
	}
}
