package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.JMAFeed;
import net.teamfruit.eewbot.entity.jma.JMAStatus;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE51;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE52;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SuppressWarnings("NonAsciiCharacters")
public class JMAXmlLGateway implements Gateway<AbstractJMAReport> {

    private static final String REMOTE_ROOT = "https://www.data.jma.go.jp/developer/xml/feed/";

    private static final String REMOTE = "eqvol_l.xml";

    private final QuakeInfoStore store;

    public JMAXmlLGateway(QuakeInfoStore store) {
        this.store = store;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-jmaxml-thread");

            HttpRequest.Builder feedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(REMOTE_ROOT + REMOTE))
                    .header("User-Agent", "eewbot")
                    .GET();

            HttpResponse<InputStream> feedResponse = EEWBot.instance.getHttpClient().send(feedRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (feedResponse.statusCode() != 200) {
                Log.logger.warn("Failed to fetch JMA XML: HTTP " + feedResponse.statusCode());
                return;
            }

            JMAFeed feed = EEWBot.XML_MAPPER.readValue(new InputStreamReader(feedResponse.body()), JMAFeed.class);

            boolean searchVXSE51 = false;
            long searchEventId = -1;
            for (JMAFeed.Entry entry : feed.getEntries()) {
                if (entry.getTitle().isEmpty()) {
                    Log.logger.warn("Unknown JMA XML Activity: {}", entry);
                    continue;
                }

                Class<? extends AbstractJMAReport> reportClass = entry.getTitle().get().getReportClass();
                if (reportClass == null || !QuakeInfo.class.isAssignableFrom(reportClass)) {
                    continue;
                }
                if (searchVXSE51 && !VXSE51.class.isAssignableFrom(reportClass)) {
                    continue;
                }

                HttpRequest reportRequest = HttpRequest.newBuilder()
                        .uri(URI.create(entry.getLink().getHref()))
                        .header("User-Agent", "eewbot")
                        .GET()
                        .build();
                HttpResponse<InputStream> reportResponse = EEWBot.instance.getHttpClient().send(reportRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (reportResponse.statusCode() != 200) {
                    Log.logger.warn("Failed to fetch JMA XML Report: HTTP " + reportResponse.statusCode());
                    continue;
                }

                AbstractJMAReport report = EEWBot.XML_MAPPER.readValue(new InputStreamReader(reportResponse.body()), reportClass);
                if (report.getControl().getStatus() == JMAStatus.通常 && (searchEventId < 0 || searchEventId == report.getEventId())) {
                    QuakeInfo quakeInfo = (QuakeInfo) report;
                    this.store.putReport(quakeInfo);
                    if (report instanceof VXSE52) {
                        searchVXSE51 = true;
                        searchEventId = quakeInfo.getEventId();
                    } else {
                        break;
                    }
                }
            }
        } catch (final Exception e) {
            onError(new EEWGatewayException(e));
        }
    }

    @Override
    public final void onNewData(AbstractJMAReport data) {
    }
}
