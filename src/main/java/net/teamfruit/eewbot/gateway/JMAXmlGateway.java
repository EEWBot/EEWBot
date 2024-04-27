package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.JMAFeed;
import net.teamfruit.eewbot.entity.jma.JMAStatus;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

@SuppressWarnings("NonAsciiCharacters")
public abstract class JMAXmlGateway implements Gateway<AbstractJMAReport> {

    public static final String REMOTE_ROOT = "https://www.data.jma.go.jp/developer/xml/feed/";
//    public static final String REMOTE_ROOT = "http://localhost:8000/";

    public static final String REMOTE = "eqvol.xml";

    private final QuakeInfoStore store;

    private String lastModified;
    private List<String> lastIds;

    public JMAXmlGateway(QuakeInfoStore store) {
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
            if (this.lastModified != null) {
                feedRequest.header("If-Modified-Since", this.lastModified);
            }

            HttpResponse<InputStream> feedResponse = EEWBot.instance.getHttpClient().send(feedRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (feedResponse.statusCode() == 304) {
                return;
            }
            if (feedResponse.statusCode() != 200) {
                Log.logger.warn("Failed to fetch JMA XML: HTTP " + feedResponse.statusCode());
                return;
            }

            JMAFeed feed = EEWBot.XML_MAPPER.readValue(new InputStreamReader(feedResponse.body()), JMAFeed.class);

            if (this.lastIds != null) {
                final List<String> list = feed.getEntries().stream()
                        .map(JMAFeed.Entry::getId)
                        .collect(Collectors.toList());
                final List<String> newer = new ArrayList<>(list);
                newer.removeAll(this.lastIds);
                this.lastIds = list;

                for (final ListIterator<String> it = newer.listIterator(newer.size()); it.hasPrevious(); ) {
                    String id = it.previous();
                    feed.getEntries().stream().filter(entry -> entry.getId().equals(id)).findFirst().ifPresent(entry -> {
                        if (entry.getTitle() == null) {
                            Log.logger.warn("Unknown JMA XML Activity: {}", entry);
                            return;
                        }

                        entry.getTitle().getReportClass().ifPresent(reportClass -> {
                            try {
                                HttpRequest reportRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(entry.getLink().getHref()))
//                                        .uri(URI.create(entry.getLink().getHref().replace("https://www.data.jma.go.jp/developer/xml/data/", "http://localhost:8000/")))
                                        .header("User-Agent", "eewbot")
                                        .GET()
                                        .build();
                                HttpResponse<InputStream> reportResponse = EEWBot.instance.getHttpClient().send(reportRequest, HttpResponse.BodyHandlers.ofInputStream());
                                if (reportResponse.statusCode() != 200) {
                                    Log.logger.warn("Failed to fetch JMA XML Report: HTTP " + reportResponse.statusCode());
                                    return;
                                }

                                AbstractJMAReport report = EEWBot.XML_MAPPER.readValue(new InputStreamReader(reportResponse.body()), reportClass);

                                if (report.getControl().getStatus() == JMAStatus.通常) {
                                    if (report instanceof QuakeInfo) {
                                        this.store.putReport((QuakeInfo) report);
                                    }
                                    Log.logger.info("JMA XML Report: {}", report);
                                    onNewData(report);
                                }

//                                Thread.sleep(1000L);
                            } catch (final Exception e) {
                                onError(new EEWGatewayException(e));
                            }
                        });
                    });
                }
            } else {
                this.lastIds = feed.getEntries().stream()
                        .map(JMAFeed.Entry::getId)
                        .collect(Collectors.toList());
//                this.lastIds = new ArrayList<>();
            }

            this.lastModified = feedResponse.headers().firstValue("Last-Modified").orElseThrow();
        } catch (final Exception e) {
            onError(new EEWGatewayException(e));
        }
    }
}
