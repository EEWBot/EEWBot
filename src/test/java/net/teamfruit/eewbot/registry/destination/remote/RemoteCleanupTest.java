package net.teamfruit.eewbot.registry.destination.remote;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteCleanupTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ID = RemoteApiFixture.ID;
    private static final String SECRET = RemoteApiFixture.SECRET;
    private static final String URL = "https://discord.com/api/webhooks/1234567890123456792/synthetic-webhook-token?thread_id=1234567890123456791";
    private static final long TARGET = 1234567890123456791L;
    private HttpServer server;
    private ExecutorService serverExecutor;
    private RemoteCleanupApiClient client;
    private RemoteDestinationCleanup cleanup;
    private ListAppender<ILoggingEvent> logs;
    private final CopyOnWriteArrayList<Captured> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{\"affected\":2}");
    private volatile CountDownLatch gate;
    private volatile long delayMillis;
    private volatile boolean slowBody;
    private volatile String redirect;
    private volatile Runnable afterMutation = () -> { };

    @BeforeEach void setup() throws Exception {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.serverExecutor = Executors.newCachedThreadPool();
        this.server.setExecutor(this.serverExecutor);
        this.server.createContext("/", exchange -> {
            int concurrent = this.active.incrementAndGet();
            this.maxActive.accumulateAndGet(concurrent, Math::max);
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                this.requests.add(new Captured(path, exchange.getRequestMethod(), body,
                        exchange.getRequestHeaders().getFirst("CF-Access-Client-Id"),
                        exchange.getRequestHeaders().getFirst("CF-Access-Client-Secret"), System.nanoTime()));
                if (this.gate != null) this.gate.await(5, TimeUnit.SECONDS);
                if (!this.slowBody && this.delayMillis > 0) Thread.sleep(this.delayMillis);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (this.redirect != null) exchange.getResponseHeaders().set("Location", this.redirect);
                exchange.sendResponseHeaders(this.status.get(), 0);
                if (this.slowBody) {
                    exchange.getResponseBody().write(' ');
                    exchange.getResponseBody().flush();
                    Thread.sleep(this.delayMillis);
                }
                if (this.status.get() == 200) this.afterMutation.run();
                exchange.getResponseBody().write(this.responseBody.get().getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { this.active.decrementAndGet(); }
        });
        this.server.start();
        this.client = client(2000);
        this.logs = new ListAppender<>();
        this.logs.start();
        ((Logger) LoggerFactory.getLogger(EEWBot.class)).addAppender(this.logs);
    }

    @AfterEach void close() {
        if (this.gate != null) this.gate.countDown();
        if (this.cleanup != null) this.cleanup.close();
        this.client.close();
        this.server.stop(0);
        this.serverExecutor.shutdownNow();
        ((Logger) LoggerFactory.getLogger(EEWBot.class)).detachAppender(this.logs);
        assertThat(this.logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(ID, SECRET, "synthetic-webhook-token", URL);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private String origin() { return "http://127.0.0.1:" + this.server.getAddress().getPort(); }
    private ConfigV2.Remote config(long timeout, boolean enabled) {
        return Codecs.GSON.fromJson("{\"baseUrl\":\"" + origin() + "\",\"requestTimeoutMillis\":" + timeout
                + ",\"connectTimeoutMillis\":100,\"cleanupEnabled\":" + enabled + "}", ConfigV2.Remote.class);
    }
    private RemoteCleanupApiClient client(long timeout) { return new RemoteCleanupApiClient(config(timeout, true), ID, SECRET, true); }
    private void queue(int capacity, long shutdown, long countWait) {
        this.cleanup = new RemoteDestinationCleanup(this.client, capacity, shutdown, countWait);
    }
    private void mutateGuild() { this.client.mutate("guild", Map.of("guildId", "1")); }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test void commonCredentialsAndAllContractsKeepTokensOutOfBodies() throws Exception {
        queue(256, 5000, 5000);
        this.cleanup.guildDeleted(1234567890123456789L);
        this.cleanup.destinationDeleted(TARGET);
        this.cleanup.destinationsUnavailable(List.of(TARGET, TARGET, Long.MAX_VALUE));
        this.cleanup.webhooksUnavailable(Map.of(TARGET, new DeliveryTarget(TARGET, "ja_jp", URL)));
        assertThat(this.cleanup.webhookUrlsUnavailable(List.of(URL, URL.replace("thread_id=", "other=")))).isEqualTo(2);
        this.cleanup.close();
        assertThat(this.requests).hasSize(5).allSatisfy(r -> {
            assertThat(r.id()).isEqualTo(ID);
            assertThat(r.secret()).isEqualTo(SECRET);
            assertThat(r.body()).doesNotContain("synthetic-webhook-token", "https://", SECRET);
        });
        assertThat(this.requests).allSatisfy(r -> assertThat(r.method()).isEqualTo("POST"));
        assertThat(JSON.readTree(this.requests.get(0).body())).isEqualTo(JSON.readTree("{\"guildId\":\"1234567890123456789\"}"));
        assertThat(JSON.readTree(this.requests.get(2).body()).get("targetIds")).hasSize(2);
        JsonNode guarded = JSON.readTree(this.requests.get(3).body()).get("targets").get(0);
        assertThat(guarded.get("targetId").textValue()).isEqualTo(Long.toString(TARGET));
        assertThat(guarded.get("expectedWebhookId").textValue()).isEqualTo("1234567890123456792");
        assertThat(this.requests.getLast().path()).isEqualTo("/internal/v2/cleanup/webhook-ids");
        assertThat(JSON.readTree(this.requests.getLast().body()).get("webhookIds")).hasSize(1);
        assertThat(this.maxActive).hasValue(1);
    }

    @Test void splitsAt500AndRetainsExactSigned64BitStrings() throws Exception {
        queue(256, 5000, 5000);
        var ids = java.util.stream.LongStream.rangeClosed(1, 1001).boxed().toList();
        this.cleanup.destinationsUnavailable(ids);
        this.cleanup.close();
        assertThat(this.requests).hasSize(3);
        assertThat(JSON.readTree(this.requests.get(0).body()).get("targetIds")).hasSize(500);
        assertThat(JSON.readTree(this.requests.get(1).body()).get("targetIds")).hasSize(500);
        assertThat(JSON.readTree(this.requests.get(2).body()).get("targetIds").get(0).textValue()).isEqualTo("1001");
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 405, 429, 503})
    void rejectsStatusesWithoutRetryOrSecretExposure(int status) {
        this.status.set(status);
        this.responseBody.set(SECRET + URL);
        assertThatThrownBy(() -> this.client.mutate("guild", Map.of("guildId", "1")))
                .isInstanceOf(RemoteConfigException.class).hasMessageContaining("status " + status)
                .hasMessageNotContaining(SECRET).hasMessageNotContaining(URL).hasNoCause();
        assertThat(this.requests).hasSize(1);
    }

    @ParameterizedTest @ValueSource(ints = {429, 503})
    void backsOffSubsequentIndependentOperationsWithoutReplayingFailedWrite(int status) throws Exception {
        this.status.set(status);
        queue(256, 5000, 5000);
        this.cleanup.guildDeleted(1);
        this.cleanup.destinationDeleted(2);
        await(() -> this.requests.size() == 2);
        this.cleanup.close();
        assertThat(this.requests.get(1).time() - this.requests.get(0).time()).isGreaterThan(TimeUnit.MILLISECONDS.toNanos(450));
        assertThat(this.requests.get(0).path()).endsWith("/guild");
        assertThat(this.requests.get(1).path()).endsWith("/destinations");
    }

    @Test void boundsQueueAndNeverBlocksEventSubmitter() throws Exception {
        this.gate = new CountDownLatch(1);
        queue(2, 5000, 5000);
        this.cleanup.guildDeleted(1);
        await(() -> this.requests.size() == 1);
        long start = System.nanoTime();
        for (int i = 2; i <= 20; i++) this.cleanup.destinationDeleted(i);
        assertThat(System.nanoTime() - start).isLessThan(TimeUnit.MILLISECONDS.toNanos(500));
        assertThat(this.logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("queue full or closed"));
        this.gate.countDown();
        this.cleanup.close();
        assertThat(this.requests).hasSize(3);
        assertThat(this.maxActive).hasValue(1);
    }

    @Test void shutdownWaitsForPendingWorkAndRejectsNewWork() throws Exception {
        queue(2, 5000, 5000);
        this.cleanup.guildDeleted(1);
        this.cleanup.destinationDeleted(2);
        this.cleanup.save();
        this.cleanup.close();
        this.cleanup.destinationDeleted(3);
        assertThat(this.requests).hasSize(2);
        assertThat(this.logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("queue full or closed"));
        assertThatThrownBy(this::mutateGuild).hasMessageContaining("closed");
    }

    @Test void shutdownDeadlineWarnsAndCancelsPendingOperations() throws Exception {
        this.gate = new CountDownLatch(1);
        queue(2, 20, 5000);
        this.cleanup.guildDeleted(1);
        await(() -> this.requests.size() == 1);
        this.cleanup.destinationDeleted(2);
        this.cleanup.close();
        this.gate.countDown();
        assertThat(this.requests).hasSize(1);
        assertThat(this.logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("unfinished operation"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void responseHeaderAndBodyTimeoutsDoNotReplayAmbiguousWrites(boolean slowBody) throws Exception {
        this.client.close();
        this.client = client(100);
        this.delayMillis = 500;
        this.slowBody = slowBody;
        assertThatThrownBy(() -> this.client.mutate("guild", Map.of("guildId", "1")))
                .hasMessageContaining("outcome may be unknown").hasNoCause();
        await(() -> this.requests.size() == 1);
        Thread.sleep(550);
        assertThat(this.requests).hasSize(1);
    }

    @Test void synchronousCountWaitTimesOutAndCancelsQueuedWork() throws Exception {
        this.gate = new CountDownLatch(1);
        queue(2, 5000, 50);
        this.cleanup.guildDeleted(1);
        await(() -> this.requests.size() == 1);
        assertThatThrownBy(() -> this.cleanup.webhookUrlsUnavailable(List.of(URL)))
                .hasMessageContaining("count unavailable").hasNoCause();
        this.gate.countDown();
        this.cleanup.close();
        assertThat(this.requests).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"affected\":-1}", "{\"affected\":1.0}", "{\"affected\":\"1\"}",
            "{\"affected\":1,\"snapshot\":{}}", "{\"affected\":1,\"affected\":2}", "{", "{} {}"})
    void rejectsInvalidSuccessResponsesWithoutLeakingData(String response) {
        this.responseBody.set(response);
        assertThatThrownBy(() -> this.client.mutate("guild", Map.of("guildId", "1"))).isInstanceOf(RemoteConfigException.class);
        assertThat(this.requests).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"affected\":-1}"})
    void invalidSuccessResponsesBackOffQueuedOperationsWithoutReplaying(String response) throws Exception {
        this.responseBody.set(response);
        queue(256, 5000, 5000);
        this.cleanup.guildDeleted(1);
        this.cleanup.destinationDeleted(2);
        this.cleanup.destinationDeleted(3);
        await(() -> this.requests.size() == 3);
        this.cleanup.close();

        assertThat(this.requests).hasSize(3);
        assertThat(this.requests.get(0).path()).endsWith("/guild");
        assertThat(this.requests.get(1).path()).endsWith("/destinations");
        assertThat(this.requests.get(2).path()).endsWith("/destinations");
        assertThat(JSON.readTree(this.requests.get(0).body())).isEqualTo(JSON.readTree("{\"guildId\":\"1\"}"));
        assertThat(JSON.readTree(this.requests.get(1).body())).isEqualTo(JSON.readTree("{\"targetIds\":[\"2\"]}"));
        assertThat(JSON.readTree(this.requests.get(2).body())).isEqualTo(JSON.readTree("{\"targetIds\":[\"3\"]}"));
        // Consecutive invalid 200s must preserve the exponential failure count.
        assertThat(this.requests.get(1).time() - this.requests.get(0).time())
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(450));
        assertThat(this.requests.get(2).time() - this.requests.get(1).time())
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(950));
    }

    @Test void rejectsOversizedChunkedResponse() {
        this.responseBody.set(" ".repeat(65537));
        assertThatThrownBy(() -> this.client.mutate("guild", Map.of("guildId", "1")))
                .isInstanceOf(RemoteConfigException.class).hasNoCause();
    }

    @Test void redirectsNeverForwardSecretsToAnotherHost() throws Exception {
        try (var other = new RemoteApiFixture()) {
            this.status.set(302);
            this.redirect = other.origin() + "/steal";
            assertThatThrownBy(this::mutateGuild).hasMessageContaining("302");
            assertThat(other.total()).isZero();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"http://example.com", "https://example.com/path", "https://user@example.com", "https://example.com?secret=x", "https://example.com/#fragment"})
    void rejectsNonOriginOrInsecureUrls(String url) {
        var config = Codecs.GSON.fromJson("{\"baseUrl\":\"" + url + "\"}", ConfigV2.Remote.class);
        assertThatThrownBy(() -> new RemoteCleanupApiClient(config, ID, SECRET)).hasMessageContaining("HTTPS origin").hasNoCause();
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "bad\nvalue", "bad\u0000value"})
    void rejectsInvalidCredentialsWithoutEchoingValues(String credential) {
        assertThatThrownBy(() -> new RemoteCleanupApiClient(config(2000, true), ID, credential, true))
                .hasMessageContaining("EEWBOT_CF_ACCESS_CLIENT").hasNoCause();
    }

    @Test void rejectsMissingCredentials() {
        assertThatThrownBy(() -> new RemoteCleanupApiClient(config(2000, true), null, SECRET, true))
                .hasMessageContaining("EEWBOT_CF_ACCESS_CLIENT").hasNoCause();
    }

    @ParameterizedTest @ValueSource(strings = {"not-a-url", "https://evil.invalid/api/webhooks/123/secret",
            "https://discord.com/api/webhooks/0/secret", "https://discord.com/api/webhooks/9223372036854775808/secret",
            "https://discord.com/api/webhooks/123/secret\n"})
    void invalidWebhookReferencesNeverBecomeUnguardedMutations(String url) {
        queue(2, 5000, 5000);
        this.cleanup.webhooksUnavailable(Map.of(TARGET, new DeliveryTarget(TARGET, "ja_jp", url)));
        this.cleanup.webhooksUnavailable(List.of(TARGET));
        assertThat(this.cleanup.webhookUrlsUnavailable(List.of(url))).isZero();
        this.cleanup.close();
        assertThat(this.requests).isEmpty();
    }

    @Test void backendCreatesWriterOnlyWhenEnabledAndOwnsBothClients() throws Exception {
        try (var readerApi = new RemoteApiFixture()) {
            var disabled = new ConfigV2.Remote();
            assertThat(disabled.isCleanupEnabled()).isFalse();
            var factory = new AtomicInteger();
            try (var backend = RemoteDestinationBackend.initialize(disabled, readerApi.client(), () -> {
                factory.incrementAndGet(); throw new AssertionError();
            })) {
                assertThat(backend.getCleanup()).isInstanceOf(RemoteReadOnlyDestinationCleanup.class);
            }
            assertThat(factory).hasValue(0);
            var enabled = config(2000, true);
            try (var backend = RemoteDestinationBackend.initialize(enabled, readerApi.client(), () -> this.client)) {
                assertThat(backend.getCleanup()).isInstanceOf(RemoteDestinationCleanup.class);
            }
            assertThat(this.requests).isEmpty();
            assertThat(readerApi.badHeaders).hasValue(0);
            assertThatThrownBy(this::mutateGuild).hasMessageContaining("closed");
        }
    }

    @Test void startupFailureClosesReaderAndNeverOpensWriterWithoutSqlFallback() throws Exception {
        try (var readerApi = new RemoteApiFixture()) {
            readerApi.reply(RemoteApiFixture.REVISION, 403, "{}");
            var reader = readerApi.client();
            var factory = new AtomicInteger();
            assertThatThrownBy(() -> RemoteDestinationBackend.initialize(config(2000, true), reader, () -> {
                factory.incrementAndGet(); return this.client;
            })).hasMessageContaining("denied");
            assertThatThrownBy(reader::revision).hasMessageContaining("closed");
            assertThat(factory).hasValue(0);
            assertThat(this.requests).isEmpty();
        }
    }

    @Test void cleanupLeavesSnapshotUntouchedUntilRevisionPollReloads() throws Exception {
        try (var readerApi = new RemoteApiFixture(); var reader = readerApi.client()) {
            var source = new RemoteDestinationConfigSource(reader);
            var reloads = new ConcurrentLinkedQueue<Runnable>();
            var reload = mock(ExecutorService.class);
            doAnswer(call -> reloads.add(call.getArgument(0))).when(reload).execute(any());
            var registry = new SnapshotDeliveryRegistry(source, reload);
            registry.initializeSnapshot();
            var before = registry.getSnapshot();
            var poll = new AtomicReference<Runnable>();
            var scheduler = mock(ScheduledExecutorService.class);
            when(scheduler.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any())).thenAnswer(call -> {
                poll.set(call.getArgument(0)); return mock(ScheduledFuture.class);
            });
            var poller = new RevisionPoller(registry, source, scheduler, 10000, true);
            poller.start();
            try {
                queue(2, 5000, 5000);
                this.status.set(503);
                this.cleanup.guildDeleted(1);
                await(() -> this.requests.size() == 1 && this.active.get() == 0);
                assertThat(registry.getSnapshot()).isSameAs(before);
                this.status.set(200);
                this.afterMutation = () -> {
                    readerApi.reply(RemoteApiFixture.REVISION, 200, "{\"revision\":1}");
                    readerApi.snapshot(1, RemoteApiFixture.destination());
                };
                assertThat(this.cleanup.webhookUrlsUnavailable(List.of(URL))).isEqualTo(2);
                assertThat(registry.getSnapshot()).isSameAs(before);
                poll.get().run();
                assertThat(reloads).hasSize(1);
                reloads.remove().run();
                assertThat(registry.getSnapshot().getRevision()).isEqualTo(1);
                int snapshots = readerApi.count(RemoteApiFixture.SNAPSHOT);
                poll.get().run();
                assertThat(reloads).isEmpty();
                assertThat(readerApi.count(RemoteApiFixture.SNAPSHOT)).isEqualTo(snapshots);
            } finally { poller.stop(); }
        }
    }

    record Captured(String path, String method, String body, String id, String secret, long time) { }
}
