package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JMAXmlGatewayRetryTest {

    private static final String ROOT = "https://example.com/";
    private static final String UPDATED = "2026-07-28T16:41:00Z";
    private final MutableClock clock = new MutableClock();
    private final Map<String, Integer> hits = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private final Map<String, String> reports = new HashMap<>();
    private final List<AbstractJMAReport> dispatched = new ArrayList<>();
    private final List<String> conditionalHeaders = new ArrayList<>();
    private final String threadName = Thread.currentThread().getName();
    private String entries = "";
    private int feedStatus = 200;
    private String interruptOnce;
    private JMAXmlGateway gateway;
    private HttpClient client;
    private String reportXml;

    @BeforeEach
    void setUp() throws Exception {
        this.reportXml = Files.readString(Path.of("src/test/resources/jmaxml/vxse53/case1.xml"));
        this.client = mock(HttpClient.class);
        when(this.client.<InputStream>send(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            String path = request.uri().getPath();
            if (path.equals("/eqvol.xml")) {
                this.conditionalHeaders.add(request.headers().firstValue("If-Modified-Since").orElse(null));
                return response(this.feedStatus, "<feed xmlns=\"http://www.w3.org/2005/Atom\">" + this.entries + "</feed>");
            }
            this.hits.merge(path, 1, Integer::sum);
            if (path.equals(this.interruptOnce)) {
                this.interruptOnce = null;
                throw new InterruptedException("test interruption");
            }
            return response(this.statuses.getOrDefault(path, 200), this.reports.getOrDefault(path, this.reportXml));
        });
        this.gateway = new JMAXmlGateway(this.client, new QuakeInfoStore(), this.dispatched::add, ROOT, this.clock);
    }

    @AfterEach
    void restoreThread() {
        Thread.interrupted();
        Thread.currentThread().setName(this.threadName);
    }

    @Test
    void unchangedPublishedEntriesNeverExpireAndOnlyNewEntriesAreDelivered() {
        this.entries = entry("a", UPDATED);
        this.gateway.run();
        for (int hour = 0; hour < 48; hour++) {
            this.clock.advance(Duration.ofHours(1));
            this.gateway.run();
        }
        this.entries = entry("b", UPDATED) + entry("a", UPDATED);
        this.gateway.run();
        assertThat(this.dispatched).hasSize(2);
        assertThat(this.hits).containsEntry("/a.xml", 1).containsEntry("/b.xml", 1);
    }

    @Test
    void failureDoesNotRollbackSuccessAndRetriesEvenWhenFeedReturns304() {
        this.entries = entry("b", UPDATED) + entry("a", UPDATED);
        this.statuses.put("/b.xml", 503);
        this.gateway.run();
        assertThat(this.dispatched).hasSize(1);
        this.statuses.clear();
        this.feedStatus = 304;
        this.clock.advance(Duration.ofMinutes(1));
        this.gateway.run();
        this.gateway.run();
        assertThat(this.dispatched).hasSize(2);
        assertThat(this.hits).containsEntry("/a.xml", 1).containsEntry("/b.xml", 2);
        assertThat(this.conditionalHeaders.get(1)).isEqualTo("Tue, 28 Jul 2026 16:45:00 GMT");
    }

    @Test
    void retriesMissingEntriesEvenWhenFeedFailsAndBacksOffUnavailableUrls() {
        this.entries = entry("a", UPDATED);
        this.statuses.put("/a.xml", 404);
        this.gateway.run();
        this.entries = "";
        this.clock.advance(Duration.ofMinutes(1));
        this.gateway.run();
        this.gateway.run();
        assertThat(this.hits).containsEntry("/a.xml", 2);
        this.statuses.clear();
        this.feedStatus = 503;
        this.clock.advance(Duration.ofMinutes(2));
        this.gateway.run();
        assertThat(this.dispatched).hasSize(1);
        assertThat(this.hits).containsEntry("/a.xml", 3);
    }

    @Test
    void interruptedPollKeepsSuccessesAndAllRemainingEntries() {
        this.entries = entry("c", UPDATED) + entry("b", UPDATED) + entry("a", UPDATED);
        this.interruptOnce = "/b.xml";
        this.gateway.run();
        assertThat(Thread.interrupted()).isTrue();
        assertThat(this.dispatched).hasSize(1);
        assertThat(this.hits).doesNotContainKey("/c.xml");
        this.feedStatus = 304;
        this.gateway.run();
        assertThat(this.dispatched).hasSize(3);
        assertThat(this.hits).containsEntry("/a.xml", 1).containsEntry("/b.xml", 2).containsEntry("/c.xml", 1);
    }

    @Test
    void incompleteAndEmptyFeedsDoNotEraseRecentSuccesses() {
        this.entries = entry("b", UPDATED) + entry("a", UPDATED);
        this.gateway.run();
        this.entries = entry("a", UPDATED);
        this.gateway.run();
        this.entries = "";
        this.gateway.run();
        this.entries = entry("b", UPDATED) + entry("a", UPDATED);
        this.gateway.run();
        assertThat(this.dispatched).hasSize(2);
    }

    @Test
    void startupCatchesCorrectionsCancellationsAndUnknownTimesButSkipsOldEntries() {
        this.entries = entry("cancel", UPDATED) + entry("correction", UPDATED)
                + entry("unknown", "not-a-time") + entry("old", "2026-07-28T16:00:00Z");
        this.reports.put("/cancel.xml", this.reportXml.replace("<InfoType>発表</InfoType>", "<InfoType>取消</InfoType>"));
        this.reports.put("/correction.xml", this.reportXml.replace("<InfoType>発表</InfoType>", "<InfoType>訂正</InfoType>"));
        this.gateway.run();
        assertThat(this.dispatched).hasSize(3);
        assertThat(this.dispatched).anyMatch(AbstractJMAReport::isCancelReport);
        assertThat(this.hits).doesNotContainKey("/old.xml");
    }

    @Test
    void distinctAtomEntriesWithSameEventAndSerialAndUpdatedVersionsAreIndependent() {
        this.entries = entry("b", UPDATED) + entry("a", UPDATED);
        this.gateway.run();
        assertThat(this.dispatched.get(0).getEventId()).isEqualTo(this.dispatched.get(1).getEventId());
        this.entries = entry("b", "2026-07-28T16:42:00Z") + entry("a", UPDATED);
        this.gateway.run();
        this.gateway.run();
        assertThat(this.dispatched).hasSize(3);
        assertThat(this.hits).containsEntry("/a.xml", 1).containsEntry("/b.xml", 2);
    }

    @Test
    void parseAndQueueAcceptanceFailuresRemainRetryableWithoutBlockingOtherEntries() {
        this.gateway = new JMAXmlGateway(this.client, new QuakeInfoStore(), report -> {
            if (this.dispatched.isEmpty()) {
                this.dispatched.add(report);
                throw new RejectedExecutionException("queue unavailable");
            }
            this.dispatched.add(report);
        }, ROOT, this.clock);
        this.entries = entry("b", UPDATED) + entry("a", UPDATED);
        this.reports.put("/b.xml", "invalid xml");
        this.gateway.run();
        this.reports.clear();
        this.clock.advance(Duration.ofMinutes(1));
        this.feedStatus = 304;
        this.gateway.run();
        assertThat(this.dispatched).hasSize(3);
        assertThat(this.hits).containsEntry("/a.xml", 2).containsEntry("/b.xml", 2);
    }

    private static String entry(String id, String updated) {
        return "<entry><title>震源・震度に関する情報</title><id>" + id + "</id>"
                + "<link type=\"application/xml\" href=\"" + ROOT + id + ".xml\"/>"
                + "<updated>" + updated + "</updated></entry>";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String xml) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("Last-Modified", List.of("Tue, 28 Jul 2026 16:45:00 GMT")), (name, value) -> true));
        return response;
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-28T16:45:00Z");

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override
        public Clock withZone(ZoneId zone) { return this; }
        @Override
        public Instant instant() { return this.now; }
    }
}
