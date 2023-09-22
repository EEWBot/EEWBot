package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.entity.KmoniEEW;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public abstract class KmoniGateway implements Gateway<KmoniEEW> {

    public static final String REMOTE = "http://www.kmoni.bosai.go.jp/webservice/hypo/eew/";
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Map<Long, KmoniEEW> prev = new HashMap<>();
    private final TimeProvider time;

    public KmoniGateway(final TimeProvider time) {
        this.time = time;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-eew-thread");

            final ZonedDateTime date = this.time.offset(1000);
            final String url = REMOTE + FORMAT.format(date) + ".json";

            final HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse response = EEWBot.instance.getApacheHttpClient().execute(get)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                    try (InputStreamReader is = new InputStreamReader(response.getEntity().getContent())) {
                        final KmoniEEW eew = EEWBot.GSON.fromJson(is, KmoniEEW.class);

                        if (eew != null && eew.isEEW()) {
                            final KmoniEEW prev = this.prev.get(eew.getReportId());
                            eew.setPrev(prev);
                            if (prev == null || prev.getReportNum() < eew.getReportNum()) {
                                this.prev.put(eew.getReportId(), eew);
                                onNewData(eew);
                            }
                        } else
                            this.prev.clear();

                    }
                else
                    onError(new EEWGatewayException("Failed to fetch EEW: HTTP " + response.getStatusLine().getStatusCode()));
            }
        } catch (final Exception e) {
            onError(new EEWGatewayException("Failed to fetch EEW", e));
        }
    }
}
