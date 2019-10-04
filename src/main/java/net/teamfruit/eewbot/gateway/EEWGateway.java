package net.teamfruit.eewbot.gateway;

import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.http.HTTPException;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.entity.EEW;

public abstract class EEWGateway implements Gateway<EEW> {

	public static final String REMOTE = "http://www.kmoni.bosai.go.jp/webservice/hypo/eew/";
	public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final Map<Long, EEW> prev = new HashMap<>();
	private final TimeProvider time;

	public EEWGateway(final TimeProvider time) {
		this.time = time;
	}

	@Override
	public void run() {
		try {
			final ZonedDateTime date = this.time.offset(1000);
			final String url = REMOTE+FORMAT.format(date)+".json";

			final HttpGet get = new HttpGet(url);
			try (CloseableHttpResponse response = EEWBot.instance.getHttpClient().execute(get)) {
				if (response.getStatusLine().getStatusCode()==HttpStatus.SC_OK)
					try (InputStreamReader is = new InputStreamReader(response.getEntity().getContent())) {
						final EEW eew = EEWBot.GSON.fromJson(is, EEW.class);

						if (eew!=null&&eew.isEEW()) {
							final EEW prev = this.prev.get(eew.getReportId());
							eew.setPrev(prev);
							if (prev==null||prev.getReportNum()<eew.getReportNum()) {
								this.prev.put(eew.getReportId(), eew);
								onNewData(eew);
							}
						} else
							this.prev.clear();

					}
				else
					throw new HTTPException(response.getStatusLine().getStatusCode());
			}
		} catch (final Exception e) {
			onError(e);
		}
	}
}
