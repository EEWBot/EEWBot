package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.entity.KmoniEEW;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200)
                try (InputStreamReader is = new InputStreamReader(response.body())) {
                    final KmoniEEW eew = EEWBot.GSON.fromJson(is, KmoniEEW.class);

                    if (eew != null && eew.isEEW()) {
                        final KmoniEEW prev = this.prev.get(eew.getReportId());
                        if (prev != null)
                            eew.setPrev(prev);
                        if (prev == null || prev.getReportNum() < eew.getReportNum()) {
                            this.prev.put(eew.getReportId(), eew);
                            onNewData(eew);
                        }
                    } else
                        this.prev.clear();
                }
            else
                onError(new EEWGatewayException("Failed to fetch EEW: HTTP " + response.statusCode()));
        } catch (final Exception e) {
            onError(new EEWGatewayException("Failed to fetch EEW", e));
        }
    }
}
