package net.teamfruit.eewbot.registry.destination.remote;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import net.teamfruit.eewbot.EEWBot;
import org.slf4j.LoggerFactory;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static net.teamfruit.eewbot.registry.destination.remote.RemoteApiFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteDestinationRuntimeTest {
    private RemoteApiFixture api;
    private RemoteConfigApiClient client;
    private ExecutorService reload;
    private final Queue<Runnable> reloads = new ArrayDeque<>();
    private final AtomicReference<Runnable> poll = new AtomicReference<>();
    private RevisionPoller poller;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void setup() throws Exception {
        this.api = new RemoteApiFixture();
        this.client = this.api.client();
        this.reload = mock(ExecutorService.class);
        doAnswer(call -> this.reloads.add(call.getArgument(0))).when(this.reload).execute(any());
        this.logs = new ListAppender<>();
        this.logs.start();
        ((Logger) LoggerFactory.getLogger(EEWBot.class)).addAppender(this.logs);
    }

    @AfterEach void close() {
        if (this.poller != null) this.poller.stop();
        this.client.close();
        this.api.close();
        ((Logger) LoggerFactory.getLogger(EEWBot.class)).detachAppender(this.logs);
    }

    private SnapshotDeliveryRegistry initialize() {
        this.client.checkCompatibility();
        var source = new RemoteDestinationConfigSource(this.client);
        var registry = new SnapshotDeliveryRegistry(source, this.reload);
        registry.initializeSnapshot();
        var scheduler = mock(ScheduledExecutorService.class);
        doAnswer(call -> {
            this.poll.set(call.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(scheduler).scheduleWithFixedDelay(any(), eq(10000L), eq(10000L), eq(TimeUnit.MILLISECONDS));
        this.poller = new RevisionPoller(registry, source, scheduler, 10000, true);
        this.poller.start();
        return registry;
    }

    @Test void pollingCoalescesAndDeliveryNeverCallsHttp() {
        var registry = initialize();
        this.poll.get().run();
        assertThat(this.reloads).isEmpty();
        assertThat(this.api.count(SNAPSHOT)).isEqualTo(1);
        this.api.reply(REVISION, 200, "{\"revision\":1}");
        this.api.snapshot(2, destination());
        this.poll.get().run();
        for (int i = 0; i < 100; i++) registry.requestReload();
        assertThat(this.reloads).hasSize(1);
        this.api.reply(REVISION, 200, "{\"revision\":2}");
        this.reloads.remove().run();
        assertThat(registry.getSnapshot().getRevision()).isEqualTo(2);
        assertThat(this.reloads).isEmpty();
        this.poll.get().run();
        assertThat(this.api.count(SNAPSHOT)).isEqualTo(2);
        int before = this.api.total();
        for (int i = 0; i < 100; i++) {
            assertThat(registry.getDeliveryChannels(ChannelFilter.builder().eewAlert(true).build()).webhook())
                    .containsOnlyKeys(TARGET);
        }
        assertThat(this.api.total()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 429, 503, 200})
    void failedRuntimeReloadRetainsSnapshotWithoutImmediateRetryOrSecretLogs(int status) {
        this.api.snapshot(1, destination());
        this.api.reply(REVISION, 200, "{\"revision\":1}");
        var registry = initialize();
        var old = registry.getSnapshot();
        this.api.reply(REVISION, 200, "{\"revision\":2}");
        this.api.reply(SNAPSHOT, status, "broken-json " + SECRET + URL);
        this.poll.get().run();
        this.reloads.remove().run();
        assertThat(registry.getSnapshot()).isSameAs(old);
        assertThat(this.reloads).isEmpty();
        assertThat(this.api.count(SNAPSHOT)).isEqualTo(2);
        assertLogsSafe();
    }

    @Test void updatesDuringReloadConvergeUsingEachSnapshotsOwnRevision() {
        var registry = initialize();
        this.api.reply(REVISION, 200, "{\"revision\":1}");
        this.poll.get().run();
        this.api.snapshot(1, destination());
        this.api.reply(REVISION, 200, "{\"revision\":2}");
        this.reloads.remove().run();
        assertThat(registry.getSnapshot().getRevision()).isEqualTo(1);
        assertThat(this.reloads).hasSize(1);
        this.api.snapshot(2, destination());
        this.reloads.remove().run();
        assertThat(registry.getSnapshot().getRevision()).isEqualTo(2);
        assertThat(this.reloads).isEmpty();
    }

    @Test void malformedSecondRecordDoesNotPublishPartialSnapshot() {
        this.api.snapshot(1, destination());
        var registry = initialize();
        var old = registry.getSnapshot();
        var bad = destination();
        bad.put("targetId", "10");
        bad.put("minIntensity", 10);
        this.api.snapshot(2, destination(), bad);
        this.api.reply(REVISION, 200, "{\"revision\":2}");
        this.poll.get().run();
        this.reloads.remove().run();
        assertThat(registry.getSnapshot()).isSameAs(old);
        assertThat(this.reloads).isEmpty();
        assertLogsSafe();
    }

    @Test void transientFailureRecoversOnLaterPoll() throws Exception {
        var registry = initialize();
        var old = registry.getSnapshot();
        this.api.reply(REVISION, 503, SECRET);
        this.poll.get().run();
        assertThat(registry.getSnapshot()).isSameAs(old);
        assertThat(this.reloads).isEmpty();
        this.api.reply(REVISION, 200, "{\"revision\":3}");
        this.api.snapshot(3, destination());
        Thread.sleep(1100); // first jittered backoff is at most 1s
        this.poll.get().run();
        assertThat(this.reloads).hasSize(1);
        this.reloads.remove().run();
        assertThat(registry.getSnapshot().getRevision()).isEqualTo(3);
        assertThat(registry.getSnapshot().size()).isEqualTo(1);
        assertLogsSafe();
    }

    @Test void revisionRollbackRetainsSnapshotAndReportsRestart() {
        this.api.snapshot(10, destination());
        var registry = initialize();
        var old = registry.getSnapshot();
        this.poll.get().run(); // API revision 0
        assertThat(registry.getSnapshot()).isSameAs(old);
        assertThat(this.reloads).isEmpty();
        assertThat(this.logs.list).anySatisfy(event -> {
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage()).contains("restart");
        });
    }

    @Test void initialLoadFailureDoesNotPublishEmptySnapshot() {
        this.api.reply(SNAPSHOT, 503, SECRET + URL);
        var registry = new SnapshotDeliveryRegistry(new RemoteDestinationConfigSource(this.client), this.reload);
        assertThatThrownBy(registry::initializeSnapshot).isInstanceOf(RemoteConfigException.class);
        assertThat(registry.getSnapshot()).isNull();
        verifyNoInteractions(this.reload);
    }

    @Test void concurrentRevisionCallsSerializeNetworkRequests() throws Exception {
        this.api.replies.put(REVISION, new Reply(200, "{\"revision\":0}", 30, false));
        var source = new RemoteDestinationConfigSource(this.client);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Future<Long>>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(source::getRevision));
            for (var task : tasks) assertThat(task.get(5, TimeUnit.SECONDS)).isZero();
        }
        assertThat(this.api.maxActive).hasValue(1);
    }

    @Test void readOnlyCleanupWarnsWithoutApplyingChangesOrLoggingIdentifiers() throws Exception {
        var cleanup = new RemoteReadOnlyDestinationCleanup();
        cleanup.guildDeleted(TARGET);
        cleanup.destinationDeleted(TARGET);
        cleanup.destinationsUnavailable(List.of(TARGET));
        cleanup.webhooksUnavailable(List.of(TARGET));
        assertThat(cleanup.webhookUrlsUnavailable(List.of(URL))).isZero();
        cleanup.save();
        assertThat(this.logs.list).hasSize(5).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("NOT applied", "count=1").doesNotContain(Long.toString(TARGET));
        });
        assertThat(this.api.total()).isZero();
        assertLogsSafe();
    }

    private void assertLogsSafe() {
        for (var event : this.logs.list) {
            String text = event.getFormattedMessage() + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy()));
            assertThat(text).doesNotContain(ID, SECRET, URL, "synthetic-token", "CF-Access-Client");
        }
    }
}
