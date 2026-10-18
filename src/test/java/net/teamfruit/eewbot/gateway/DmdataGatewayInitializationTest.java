package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataContract;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataError;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketList;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketStart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(10)
class DmdataGatewayInitializationTest {

    private final HttpClient client = mock(HttpClient.class);
    private final DmdataAPI api = mock(DmdataAPI.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final WebSocket.Builder builder = mock(WebSocket.Builder.class);
    private final Queue<FutureTask<?>> jobs = new ArrayDeque<>();
    private final List<URI> urls = new ArrayList<>();
    private final List<WebSocket.Listener> listeners = new ArrayList<>();
    private final List<WebSocket> sockets = new ArrayList<>();
    private final List<DmdataEEWUpdate> updates = new ArrayList<>();
    private final String threadName = Thread.currentThread().getName();
    private final DmdataContract contract = Codecs.GSON.fromJson(
            "{\"items\":[{\"classification\":\"eew.forecast\"}]}", DmdataContract.class);
    private final DmdataSocketList socketList = Codecs.GSON.fromJson("{\"items\":[]}", DmdataSocketList.class);
    private final DmdataSocketStart.Response ticket = Codecs.GSON.fromJson(
            "{\"ticket\":\"test-ticket\"}", DmdataSocketStart.Response.class);
    private DmdataGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        this.gateway = gateway(false, "");
        when(this.api.contract()).thenReturn(this.contract);
        when(this.api.openSocketList()).thenReturn(this.socketList);
        doReturn(this.ticket).when(this.api).socketStart(any());
        when(this.client.newWebSocketBuilder()).thenReturn(this.builder);
        when(this.builder.buildAsync(any(), any())).thenAnswer(invocation -> {
            this.urls.add(invocation.getArgument(0));
            this.listeners.add(invocation.getArgument(1));
            WebSocket socket = mock(WebSocket.class);
            this.sockets.add(socket);
            return CompletableFuture.completedFuture(socket);
        });
        when(this.scheduler.submit(any(Runnable.class))).thenAnswer(invocation -> {
            FutureTask<Void> job = new FutureTask<>(invocation.getArgument(0, Runnable.class), null);
            this.jobs.add(job);
            return job;
        });
    }

    @AfterEach
    void tearDown() {
        this.gateway.close();
        Thread.interrupted();
        Thread.currentThread().setName(this.threadName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"contract", "list", "start", "api-error"})
    void initialApiFailureIsRetriedAndRecoveredSocketReceivesEew(String stage) throws Exception {
        if (stage.equals("contract"))
            when(this.api.contract()).thenThrow(new IOException("unavailable")).thenReturn(this.contract);
        else if (stage.equals("api-error"))
            when(this.api.contract()).thenThrow(new DmdataGatewayException(Codecs.GSON.fromJson(
                    "{\"error\":{\"code\":503,\"message\":\"unavailable\"}}", DmdataError.class))).thenReturn(this.contract);
        else if (stage.equals("list"))
            when(this.api.openSocketList()).thenThrow(new IOException("unavailable")).thenReturn(this.socketList);
        else
            when(this.api.socketStart(any())).thenThrow(new IOException("unavailable")).thenReturn(this.ticket);
        this.gateway.run();
        assertThat(this.gateway.getWebSocket1()).isNull();
        var checker = new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler);
        checker.run();
        checker.run();
        assertThat(this.jobs).hasSize(1); // A queued retry is not submitted twice.
        runJobs();
        assertThat(this.gateway.getWebSocket1()).isNotNull();
        assertThat(this.gateway.getWebSocket1().getWebSocket()).isNotNull();
        assertThat(this.gateway.needsInitialization()).isFalse();
        String xml = Files.readString(Path.of("src/test/resources/dmdata/eew/forecast_normal.xml"));
        String message = Codecs.GSON.toJson(Map.of("type", "data", "version", "2.0", "id", "after-recovery",
                "head", Map.of("type", "VXSE45", "test", false), "body", xml));
        this.listeners.getFirst().onText(this.sockets.getFirst(), message, true);
        assertThat(this.updates).hasSize(1);
        checker.run();
        runJobs();
        assertThat(this.urls).hasSize(1);
        verify(this.api, times(2)).contract();
    }

    @Test
    void initializationRetryUsesRecoveredWarningOnlyContract() throws Exception {
        var warning = Codecs.GSON.fromJson("{\"items\":[{\"classification\":\"eew.warning\"}]}", DmdataContract.class);
        when(this.api.contract()).thenThrow(new IOException("unavailable")).thenReturn(warning);
        this.gateway.run();
        new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler).run();
        runJobs();
        var requests = org.mockito.ArgumentCaptor.forClass(DmdataSocketStart.Request.class);
        verify(this.api).socketStart(requests.capture());
        assertThat(requests.getValue().getClassifications()).containsExactly("eew.warning");
        assertThat(requests.getValue().getTypes()).containsExactly("VXSE43");
        assertThat(this.gateway.getWebSocket1().hasForecastContract()).isFalse();
    }

    @Test
    void failedInitializationCanBeRetriedOverMultipleLivenessChecks() throws Exception {
        when(this.api.contract()).thenThrow(new IOException("unavailable")).thenThrow(new IOException("still unavailable"))
                .thenReturn(this.contract);
        var checker = new DmdataWsLivenessChecker(this.gateway, this.scheduler);
        this.gateway.run();
        checker.run();
        runJobs();
        assertThat(this.gateway.getWebSocket1()).isNull();
        checker.run();
        runJobs();
        assertThat(this.gateway.getWebSocket1()).isNotNull();
        verify(this.api, times(3)).contract();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void multiConnectionKeepsHealthySideAndRetriesOnlyMissingSide(boolean firstFails) throws Exception {
        this.gateway = gateway(true, "");
        when(this.api.socketStart(any())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0, DmdataSocketStart.Request.class).getAppName();
            if (name.endsWith(firstFails ? "-1" : "-2"))
                throw new IOException("one endpoint unavailable");
            return this.ticket;
        });
        this.gateway.run();
        var healthy = firstFails ? this.gateway.getWebSocket2() : this.gateway.getWebSocket1();
        assertThat(healthy).isNotNull();
        assertThat(firstFails ? this.gateway.getWebSocket1() : this.gateway.getWebSocket2()).isNull();
        assertThatCode(this.gateway::reconnectDeadWebSocketsBasedOnDmData).doesNotThrowAnyException();
        doReturn(this.ticket).when(this.api).socketStart(any());
        new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler).run();
        runJobs();
        assertThat(this.gateway.getWebSocket1()).isNotNull();
        assertThat(this.gateway.getWebSocket2()).isNotNull();
        assertThat(firstFails ? this.gateway.getWebSocket2() : this.gateway.getWebSocket1()).isSameAs(healthy);
        verify(this.api, times(3)).socketStart(any());
        assertThat(this.urls).hasSize(2);
    }

    @Test
    void debugModeRetriesSynchronousHandshakeSetupFailureWithoutDmdataApi() throws Exception {
        this.gateway = gateway(false, "wss://test.invalid");
        WebSocket socket = mock(WebSocket.class);
        when(this.builder.buildAsync(any(), any())).thenThrow(new IllegalStateException("handshake setup failed"))
                .thenReturn(CompletableFuture.completedFuture(socket));
        this.gateway.run();
        new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler).run();
        runJobs();
        assertThat(this.gateway.getWebSocket1().getWebSocket()).isSameAs(socket);
        verifyNoInteractions(this.api);
    }

    @Test
    void failedAsyncHandshakeStillUsesExistingConnectionReconnectPath() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        when(this.builder.buildAsync(any(), any())).thenReturn(CompletableFuture.failedFuture(new IOException("handshake failed")))
                .thenReturn(CompletableFuture.completedFuture(socket));
        this.gateway.run();
        var failed = this.gateway.getWebSocket1();
        assertThat(failed.isReconnectFailed()).isTrue();
        new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler).run();
        runJobs();
        assertThat(this.gateway.getWebSocket1()).isNotSameAs(failed);
        assertThat(this.gateway.getWebSocket1().getWebSocket()).isSameAs(socket);
        assertThat(failed.replaced).isTrue();
        verify(this.api, times(2)).socketStart(any());
    }

    @Test
    void missingConnectionsAreSafeForServerSideDeadCheck() {
        assertThatCode(this.gateway::reconnectDeadWebSocketsBasedOnDmData).doesNotThrowAnyException();
        verifyNoInteractions(this.api);
    }

    @Test
    void closeBeforeQueuedInitializationPreventsNetworkCalls() throws Exception {
        when(this.api.contract()).thenThrow(new IOException("unavailable"));
        this.gateway.run();
        var checker = new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler);
        checker.run();
        assertThat(this.jobs).hasSize(1);
        this.gateway.close();
        runJobs();
        checker.run();
        this.gateway.run();
        assertThat(this.jobs).isEmpty();
        verify(this.api).contract();
        verifyNoInteractions(this.client);
    }

    @Test
    void closeDuringSocketStartDoesNotBeginHandshake() throws Exception {
        when(this.api.socketStart(any())).thenAnswer(invocation -> {
            this.gateway.close();
            return this.ticket;
        });
        this.gateway.run();
        assertThat(this.gateway.getWebSocket1()).isNull();
        assertThat(this.gateway.needsInitialization()).isFalse();
        verifyNoInteractions(this.client);
    }

    @Test
    void handshakeCompletingAfterShutdownIsClosedInsteadOfRetained() {
        CompletableFuture<WebSocket> handshake = new CompletableFuture<>();
        when(this.builder.buildAsync(any(), any())).thenReturn(handshake);
        this.gateway.run();
        this.gateway.close();
        WebSocket socket = mock(WebSocket.class);
        handshake.complete(socket);
        verify(socket).sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        assertThat(this.gateway.getWebSocket1().getWebSocket()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"contract", "list", "start"})
    void interruptionDuringInitialApiCallRestoresInterruptAndCanRetryLater(String stage) throws Exception {
        if (stage.equals("contract"))
            when(this.api.contract()).thenThrow(new InterruptedException("interrupted")).thenReturn(this.contract);
        else if (stage.equals("list"))
            when(this.api.openSocketList()).thenThrow(new InterruptedException("interrupted")).thenReturn(this.socketList);
        else
            when(this.api.socketStart(any())).thenThrow(new InterruptedException("interrupted")).thenReturn(this.ticket);
        this.gateway.run();
        assertThat(Thread.interrupted()).isTrue();
        assertThat(this.gateway.needsInitialization()).isTrue();
        new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler).run();
        runJobs();
        assertThat(this.gateway.getWebSocket1()).isNotNull();
    }

    @Test
    void repeatedChecksDuringInitialAttemptDoNotStartDuplicateConnections() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(this.api.contract()).thenAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return this.contract;
        });
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> startup = pool.submit(this.gateway);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var checker = new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler);
                checker.run();
                this.gateway.run();
                assertThat(this.jobs).isEmpty();
                this.gateway.close();
            } finally {
                release.countDown();
            }
            startup.get(5, TimeUnit.SECONDS);
        }
        verify(this.api).contract();
        verify(this.api, never()).openSocketList();
        verifyNoInteractions(this.client);
    }

    @Test
    void rejectedRetrySubmissionDoesNotDisableFutureLivenessChecks() throws Exception {
        when(this.api.contract()).thenThrow(new IOException("unavailable")).thenReturn(this.contract);
        this.gateway.run();
        var checker = new DmdataWsDebugLivenessChecker(this.gateway, this.scheduler);
        when(this.scheduler.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("shutting down"))
                .thenAnswer(invocation -> {
                    FutureTask<Void> job = new FutureTask<>(invocation.getArgument(0, Runnable.class), null);
                    this.jobs.add(job);
                    return job;
                });
        assertThatCode(checker::run).doesNotThrowAnyException();
        checker.run();
        runJobs();
        assertThat(this.gateway.getWebSocket1()).isNotNull();
    }

    private DmdataGateway gateway(boolean multi, String wsBase) {
        return new DmdataGateway(this.client, this.api, 1L, multi, this.updates::add, this.scheduler, wsBase);
    }

    private void runJobs() throws Exception {
        while (!this.jobs.isEmpty()) {
            FutureTask<?> job = this.jobs.remove();
            job.run();
            job.get(5, TimeUnit.SECONDS);
        }
    }
}
