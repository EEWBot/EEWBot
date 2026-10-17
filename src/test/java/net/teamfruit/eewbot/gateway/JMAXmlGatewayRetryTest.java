package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CompletableFuture;

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
    private final List<HttpRequest> requests = new ArrayList<>();
    private final Set<String> timedOut = new HashSet<>();
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
        when(this.client.<byte[]>sendAsync(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            this.requests.add(request);
            String path = request.uri().getPath();
            if (this.timedOut.contains(path)) {
                if (!path.equals("/eqvol.xml"))
                    this.hits.merge(path, 1, Integer::sum);
                return CompletableFuture.failedFuture(new HttpTimeoutException("test timeout"));
            }
            if (path.equals("/eqvol.xml")) {
                this.conditionalHeaders.add(request.headers().firstValue("If-Modified-Since").orElse(null));
                return CompletableFuture.completedFuture(response(this.feedStatus,
                        "<feed xmlns=\"http://www.w3.org/2005/Atom\">" + this.entries + "</feed>"));
            }
            this.hits.merge(path, 1, Integer::sum);
            if (path.equals(this.interruptOnce)) {
                this.interruptOnce = null;
                return CompletableFuture.failedFuture(new InterruptedException("test interruption"));
            }
            return CompletableFuture.completedFuture(response(this.statuses.getOrDefault(path, 200),
                    this.reports.getOrDefault(path, this.reportXml)));
        });
        this.gateway = new JMAXmlGateway(this.client, new QuakeInfoStore(), this.dispatched::add, ROOT, this.clock);
    }

    @AfterEach
    void restoreThread() {
        Thread.interrupted();
        Thread.currentThread().setName(this.threadName);
    }

    @Test
    void reportTimeoutContinuesLaterReportsAndEntersBackoff() {
        this.entries = entry("b", UPDATED) + entry("a", UPDATED);
        this.timedOut.add("/a.xml");
        this.gateway.run();
        assertThat(this.dispatched).hasSize(1);
        this.clock.advance(Duration.ofSeconds(59));
        this.gateway.run();
        assertThat(this.hits).containsEntry("/a.xml", 1).containsEntry("/b.xml", 1);
        this.timedOut.clear();
        this.clock.advance(Duration.ofSeconds(1));
        this.feedStatus = 304;
        this.gateway.run();
        assertThat(this.dispatched).hasSize(2);
        assertThat(this.hits).containsEntry("/a.xml", 2).containsEntry("/b.xml", 1);
        assertThat(this.requests).allSatisfy(request ->
                assertThat(request.timeout()).contains(Duration.ofSeconds(5)));
    }

    @Test
    void feedTimeoutStillProcessesDueReportsAndKeepsConditionalRequests() {
        this.entries = entry("a", UPDATED);
        this.statuses.put("/a.xml", 503);
        this.gateway.run();
        this.clock.advance(Duration.ofMinutes(1));
        this.statuses.clear();
        this.timedOut.add("/eqvol.xml");
        this.gateway.run();
        this.timedOut.clear();
        this.feedStatus = 304;
        this.gateway.run();
        assertThat(this.dispatched).hasSize(1);
        assertThat(this.hits).containsEntry("/a.xml", 2);
        assertThat(this.requests.stream().filter(request -> request.uri().getPath().equals("/eqvol.xml")).skip(1))
                .allSatisfy(request -> assertThat(request.headers().firstValue("If-Modified-Since"))
                        .contains("Tue, 28 Jul 2026 16:45:00 GMT"));
    }

    @Test
    void unattemptedReportsDeferredByBudgetAreRetainedThrough304() {
        for (int i = 0; i < 20; i++)
            this.entries = entry("report" + i, UPDATED) + this.entries;
        this.gateway.run();
        assertThat(this.dispatched).hasSize(7);
        this.feedStatus = 304;
        this.gateway.run();
        assertThat(this.dispatched).hasSize(14);
        this.gateway.run();
        this.gateway.run();
        assertThat(this.dispatched).hasSize(20);
        assertThat(this.hits.values()).containsOnly(1);
    }

    @Test
    void unsupportedEntriesDoNotConsumeHttpAttemptBudget() {
        this.entries = entry("report", UPDATED);
        for (int i = 0; i < 10; i++)
            this.entries += entry("unsupported" + i, UPDATED).replace("震源・震度に関する情報", "季節観測");
        this.gateway.run();
        assertThat(this.dispatched).hasSize(1);
        assertThat(this.hits).containsOnlyKeys("/report.xml");
    }

    @Test
    void retryBudgetPrioritizesNewReportsAndRotatesLargeDueBacklog() {
        for (int i = 0; i < 12; i++) {
            this.entries = entry("failed" + i, UPDATED) + this.entries;
            this.statuses.put("/failed" + i + ".xml", 503);
        }
        this.gateway.run();
        this.gateway.run(); // Remaining first attempts; no retry is due yet.
        this.clock.advance(Duration.ofMinutes(1));
        this.entries = entry("fresh", UPDATED) + this.entries;
        int before = this.requests.size();
        this.gateway.run();
        assertThat(this.requests.subList(before, this.requests.size())).extracting(request -> request.uri().getPath())
                .containsExactly("/eqvol.xml", "/fresh.xml", "/failed0.xml", "/failed1.xml", "/failed2.xml");
        assertThat(this.dispatched).hasSize(1);
        this.feedStatus = 304;
        for (int poll = 0; poll < 3; poll++) {
            before = this.requests.size();
            this.gateway.run();
            assertThat(this.requests.size() - before).isEqualTo(4); // Feed plus three retries.
        }
        assertThat(this.hits).containsEntry("/fresh.xml", 1);
        for (int i = 0; i < 12; i++)
            assertThat(this.hits).containsEntry("/failed" + i + ".xml", 2);
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
    private static HttpResponse<byte[]> response(int status, String xml) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(xml.getBytes(StandardCharsets.UTF_8));
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
