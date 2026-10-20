package net.teamfruit.eewbot.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(15)
class JMAXmlGatewayTimeoutTest {

    private static final String LAST_MODIFIED = "Tue, 28 Jul 2026 16:45:00 GMT";
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch stalled = new CountDownLatch(1);
    private final List<AbstractJMAReport> dispatched = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private final String threadName = Thread.currentThread().getName();
    private HttpServer server;
    private ExecutorService serverExecutor;
    private HttpClient client;
    private String root;
    private String reportXml;

    enum Stall {
        HEADERS, BODY
    }

    @BeforeEach
    void setUp() throws IOException {
        this.reportXml = Files.readString(Path.of("src/test/resources/jmaxml/vxse53/case1.xml"));
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.server.setExecutor(this.serverExecutor);
        this.root = "http://127.0.0.1:" + this.server.getAddress().getPort() + "/";
        this.client = HttpClient.newHttpClient();
        this.server.start();
    }

    @AfterEach
    void tearDown() {
        this.release.countDown();
        this.server.stop(0);
        this.serverExecutor.shutdownNow();
        this.client.close();
        Thread.interrupted();
        Thread.currentThread().setName(this.threadName);
    }

    @ParameterizedTest
    @EnumSource(Stall.class)
    void stalledReportDoesNotBlockLaterEntriesAndIsRetriedOn304(Stall stall) {
        AtomicBoolean shouldStall = new AtomicBoolean(true);
        AtomicInteger reportHits = new AtomicInteger();
        AtomicInteger laterHits = new AtomicInteger();
        AtomicInteger unchangedFeeds = new AtomicInteger();
        String feed = feed(entry("later") + entry("stalled"));
        this.server.createContext("/eqvol.xml", exchange -> {
            if (LAST_MODIFIED.equals(exchange.getRequestHeaders().getFirst("If-Modified-Since"))) {
                unchangedFeeds.incrementAndGet();
                respond(exchange, 304, "");
            } else
                respond(exchange, 200, feed);
        });
        this.server.createContext("/stalled.xml", exchange -> {
            reportHits.incrementAndGet();
            if (shouldStall.get())
                stall(exchange, stall, this.reportXml);
            else
                respond(exchange, 200, this.reportXml);
        });
        this.server.createContext("/later.xml", exchange -> {
            laterHits.incrementAndGet();
            respond(exchange, 200, this.reportXml);
        });
        JMAXmlGateway gateway = gateway(this.client, Duration.ofSeconds(1));
        gateway.run();
        assertThat(this.stalled.getCount()).isZero();
        assertThat(this.dispatched).hasSize(1);
        assertThat(laterHits.get()).isEqualTo(1);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        shouldStall.set(false);
        this.clock.advance(Duration.ofMinutes(1));
        gateway.run();
        gateway.run();
        assertThat(unchangedFeeds.get()).isEqualTo(2);
        assertThat(reportHits.get()).isEqualTo(2);
        assertThat(laterHits.get()).isEqualTo(1);
        assertThat(this.dispatched).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(Stall.class)
    void stalledFeedStillAllowsPendingRetryAndNextPoll(Stall stall) {
        AtomicBoolean shouldStall = new AtomicBoolean(false);
        AtomicInteger reportStatus = new AtomicInteger(503);
        AtomicInteger reportHits = new AtomicInteger();
        this.server.createContext("/eqvol.xml", exchange -> {
            if (shouldStall.get())
                stall(exchange, stall, feed(entry("report")));
            else
                respond(exchange, 200, feed(entry("report")));
        });
        this.server.createContext("/report.xml", exchange -> {
            reportHits.incrementAndGet();
            respond(exchange, reportStatus.get(), this.reportXml);
        });
        JMAXmlGateway gateway = gateway(this.client, Duration.ofSeconds(1));
        gateway.run();
        reportStatus.set(200);
        this.clock.advance(Duration.ofMinutes(1));
        shouldStall.set(true);
        gateway.run();
        assertThat(this.stalled.getCount()).isZero();
        assertThat(reportHits.get()).isEqualTo(2);
        assertThat(this.dispatched).hasSize(1);
        shouldStall.set(false);
        gateway.run();
        assertThat(reportHits.get()).isEqualTo(2);
        assertThat(this.dispatched).hasSize(1);
    }

    @Test
    void expiredFullBodyWaitCancelsOutstandingExchange() {
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(client.<byte[]>sendAsync(any(), any())).thenReturn(pending);
        gateway(client, Duration.ofNanos(1)).run();
        assertThat(pending.isCancelled()).isTrue();
    }

    @Test
    void interruptedFullBodyWaitCancelsExchangeAndRestoresInterrupt() {
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(client.<byte[]>sendAsync(any(), any())).thenReturn(pending);
        Thread.currentThread().interrupt();
        gateway(client, Duration.ofSeconds(1)).run();
        assertThat(Thread.interrupted()).isTrue();
        assertThat(pending.isCancelled()).isTrue();
    }

    private JMAXmlGateway gateway(HttpClient client, Duration timeout) {
        return new JMAXmlGateway(client, new QuakeInfoStore(), this.dispatched::add, this.root, this.clock, timeout);
    }

    private void stall(HttpExchange exchange, Stall stall, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            if (stall == Stall.BODY) {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes, 0, 1);
                exchange.getResponseBody().flush();
            }
            this.stalled.countDown();
            try {
                this.release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Last-Modified", LAST_MODIFIED);
            if (status == 304)
                exchange.sendResponseHeaders(status, -1);
            else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    private String entry(String id) {
        return "<entry><title>震源・震度に関する情報</title><id>" + id + "</id>"
                + "<link type=\"application/xml\" href=\"" + this.root + id + ".xml\"/>"
                + "<updated>2026-07-28T16:41:00Z</updated></entry>";
    }

    private static String feed(String entries) {
        return "<feed xmlns=\"http://www.w3.org/2005/Atom\">" + entries + "</feed>";
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
