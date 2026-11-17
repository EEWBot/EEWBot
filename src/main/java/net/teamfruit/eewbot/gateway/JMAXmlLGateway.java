package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.JMAFeed;
import net.teamfruit.eewbot.entity.jma.JMAStatus;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE51;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE52;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.MDC;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@SuppressWarnings("NonAsciiCharacters")
public class JMAXmlLGateway implements Gateway<AbstractJMAReport> {

    private static final String DEFAULT_REMOTE_ROOT = "https://www.data.jma.go.jp/developer/xml/feed/";

    private static final String REMOTE = "eqvol_l.xml";
    // Covers the whole exchange including the body; the long feed is larger than the regular one.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final java.net.http.HttpClient httpClient;
    private final QuakeInfoStore store;
    private final String remoteRoot;
    private final Runnable onInitialLoadComplete;

    public JMAXmlLGateway(java.net.http.HttpClient httpClient, QuakeInfoStore store, String debugFeedRoot) {
        this(httpClient, store, debugFeedRoot, () -> {});
    }

    public JMAXmlLGateway(java.net.http.HttpClient httpClient, QuakeInfoStore store, String debugFeedRoot, Runnable onInitialLoadComplete) {
        this.httpClient = httpClient;
        this.store = store;
        this.onInitialLoadComplete = onInitialLoadComplete;
        String root = StringUtils.isNotEmpty(debugFeedRoot) ? debugFeedRoot : DEFAULT_REMOTE_ROOT;
        this.remoteRoot = Strings.CS.appendIfMissing(root, "/");
    }

    @Override
    public void run() {
        MDC.put("gateway", "jma-xml-l");
        try {
            HttpRequest.Builder feedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(this.remoteRoot + REMOTE))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "eewbot")
                    .GET();

            HttpResponse<byte[]> feedResponse = JMAXmlGateway.send(this.httpClient, feedRequest.build(), REQUEST_TIMEOUT);
            if (feedResponse.statusCode() != 200) {
                Log.logger.warn("Failed to fetch JMA XML: HTTP " + feedResponse.statusCode());
                return;
            }

            JMAFeed feed = Codecs.XML_MAPPER.readValue(feedResponse.body(), JMAFeed.class);

            boolean searchVXSE51 = false;
            String searchEventId = null;
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
                        .timeout(REQUEST_TIMEOUT)
                        .header("User-Agent", "eewbot")
                        .GET()
                        .build();
                HttpResponse<byte[]> reportResponse = JMAXmlGateway.send(this.httpClient, reportRequest, REQUEST_TIMEOUT);
                if (reportResponse.statusCode() != 200) {
                    Log.logger.warn("Failed to fetch JMA XML Report: HTTP " + reportResponse.statusCode());
                    continue;
                }

                AbstractJMAReport report = Codecs.XML_MAPPER.readValue(reportResponse.body(), reportClass);
                if (report.getControl().getStatus() == JMAStatus.通常 && (searchEventId == null || searchEventId.equals(report.getEventId()))) {
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
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            onError(new EEWGatewayException(e));
        } finally {
            this.onInitialLoadComplete.run();
            MDC.clear();
        }
    }

    @Override
    public final void onNewData(AbstractJMAReport data) {
    }
}
