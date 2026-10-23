package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RevisionPollerTest {

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void newerSettingsWithLaggingRevisionEventuallyCatchUp(long laggingRevision) {
        DestinationConfigSource source = mock(DestinationConfigSource.class);
        var channel = new DeliverySnapshot.DeliveryChannel(123L, 123L, null, null,
                true, false, false, false, false, SeismicIntensity.ONE, "ja_jp", null);
        DeliverySnapshot initial = new DeliverySnapshot(0, List.of());
        DeliverySnapshot lagging = new DeliverySnapshot(laggingRevision, List.of(channel));
        DeliverySnapshot latest = new DeliverySnapshot(2, List.of(channel));
        when(source.loadSnapshot()).thenReturn(initial, lagging, latest);
        when(source.getRevision()).thenReturn(2L);
        ExecutorService executor = mock(ExecutorService.class);
        Queue<Runnable> reloads = new ArrayDeque<>();
        doAnswer(call -> reloads.add(call.getArgument(0))).when(executor).execute(any(Runnable.class));
        SnapshotDeliveryRegistry delivery = new SnapshotDeliveryRegistry(source, executor);
        delivery.initializeSnapshot();
        ScheduledExecutorService pollScheduler = mock(ScheduledExecutorService.class);
        AtomicReference<Runnable> poll = new AtomicReference<>();
        doAnswer(call -> {
            poll.set(call.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(pollScheduler).scheduleAtFixedRate(any(Runnable.class), eq(2000L), eq(2000L), eq(TimeUnit.MILLISECONDS));
        RevisionPoller poller = new RevisionPoller(delivery, source, pollScheduler);
        poller.start();

        poll.get().run();
        reloads.remove().run();

        // An unchanged revision can skip replacement; either case must request another load.
        assertThat(delivery.getSnapshot()).isSameAs(laggingRevision == 0 ? initial : lagging);
        assertThat(reloads).hasSize(1);
        reloads.remove().run();

        assertThat(delivery.getSnapshot()).isSameAs(latest);
        assertThat(delivery.getDeliveryChannels(null).direct()).containsOnlyKeys(123L);
        assertThat(reloads).isEmpty();
        poller.stop();
    }

    private ScheduledExecutorService scheduler;
    private ExecutorService reloadExecutor;

    @BeforeEach
    void setUp() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.reloadExecutor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        this.scheduler.shutdownNow();
        this.reloadExecutor.shutdownNow();
    }

    @Test
    @DisplayName("start() should begin polling and detect revision changes")
    void startDetectsRevisionChange() throws Exception {
        StubRevisionStore revisionStore = new StubRevisionStore(1L);
        AtomicInteger reloadCount = new AtomicInteger(0);
        CountDownLatch reloadCalled = new CountDownLatch(1);

        TestSnapshotLoader loader = new TestSnapshotLoader(
                () -> new DeliverySnapshot(revisionStore.getRevision(), List.of()));

        SnapshotDeliveryRegistry deliveryRegistry = new SnapshotDeliveryRegistry(
                new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.reloadExecutor) {
            @Override
            public void requestReload() {
                reloadCount.incrementAndGet();
                reloadCalled.countDown();
                super.requestReload();
            }
        };
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.scheduler, 50);
        poller.start();

        // Change revision to trigger reload
        revisionStore.setRevision(2L);

        // Wait for poll to detect the change
        assertThat(reloadCalled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reloadCount.get()).isGreaterThanOrEqualTo(1);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (deliveryRegistry.getSnapshot().getRevision() < 2L && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(deliveryRegistry.getSnapshot().getRevision()).isEqualTo(2L);
        poller.stop();
    }

    @Test
    @DisplayName("start() should be idempotent - double start does not create duplicate tasks")
    void doubleStartIsIdempotent() throws Exception {
        StubRevisionStore revisionStore = new StubRevisionStore(1L);
        AtomicInteger reloadCount = new AtomicInteger(0);
        TestSnapshotLoader loader = new TestSnapshotLoader(
                () -> new DeliverySnapshot(revisionStore.getRevision(), List.of()));

        SnapshotDeliveryRegistry deliveryRegistry = new SnapshotDeliveryRegistry(
                new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.reloadExecutor) {
            @Override
            public void requestReload() {
                reloadCount.incrementAndGet();
                super.requestReload();
            }
        };
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.scheduler, 100);

        // Start twice - should not throw and should not create duplicate scheduled tasks
        poller.start();
        poller.start();

        // Change revision to trigger reload detection
        revisionStore.setRevision(2L);

        // Wait for polls to fire
        Thread.sleep(350);

        // With 100ms poll interval and ~350ms wait, expect ~3 polls.
        // If double-scheduled, reload count would be roughly doubled (~6+).
        // Allow up to 5 as a safe upper bound for single schedule.
        assertThat(reloadCount.get()).as("reload count should be consistent with single schedule")
                .isLessThanOrEqualTo(5);

        poller.stop();
    }

    @Test
    @DisplayName("stop() should cancel polling")
    void stopCancelsPolling() throws Exception {
        StubRevisionStore revisionStore = new StubRevisionStore(1L);
        AtomicInteger reloadCount = new AtomicInteger(0);

        TestSnapshotLoader loader = new TestSnapshotLoader(
                () -> new DeliverySnapshot(revisionStore.getRevision(), List.of()));

        SnapshotDeliveryRegistry deliveryRegistry = new SnapshotDeliveryRegistry(
                new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.reloadExecutor) {
            @Override
            public void requestReload() {
                reloadCount.incrementAndGet();
                super.requestReload();
            }
        };
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.scheduler, 50);
        poller.start();
        poller.stop();

        // Change revision after stop
        reloadCount.set(0);
        revisionStore.setRevision(99L);

        // Wait to confirm no polls happen
        Thread.sleep(200);
        assertThat(reloadCount.get()).isZero();
    }

    @Test
    @DisplayName("stop() on never-started poller should not throw")
    void stopBeforeStart() {
        StubRevisionStore revisionStore = new StubRevisionStore(1L);
        TestSnapshotLoader loader = new TestSnapshotLoader(
                () -> new DeliverySnapshot(1L, List.of()));

        SnapshotDeliveryRegistry deliveryRegistry = new SnapshotDeliveryRegistry(
                new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.reloadExecutor);

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, new FakeDestinationConfigSource(loader, revisionStore::getRevision), this.scheduler, 100);
        // Should not throw
        poller.stop();
    }

    @Test
    @DisplayName("poll should not crash when revisionStore throws exception")
    void pollExceptionResilience() throws Exception {
        AtomicInteger pollCallCount = new AtomicInteger(0);
        CountDownLatch secondPoll = new CountDownLatch(1);

        java.util.function.LongSupplier throwingStore = () -> {
            int count = pollCallCount.incrementAndGet();
            if (count == 1) {
                throw new RuntimeException("DB connection lost");
            }
            secondPoll.countDown();
            return 1L;
        };

        TestSnapshotLoader loader = new TestSnapshotLoader(
                () -> new DeliverySnapshot(1L, List.of()));

        SnapshotDeliveryRegistry deliveryRegistry = new SnapshotDeliveryRegistry(
                new FakeDestinationConfigSource(loader, throwingStore), this.reloadExecutor);
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, new FakeDestinationConfigSource(loader, throwingStore), this.scheduler, 50);
        poller.start();

        // Second poll should still execute despite first one failing
        assertThat(secondPoll.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pollCallCount.get()).isGreaterThanOrEqualTo(2);

        poller.stop();
    }

    private static class StubRevisionStore {
        private volatile long revision;

        StubRevisionStore(long initialRevision) {
            this.revision = initialRevision;
        }

        public long getRevision() {
            return this.revision;
        }

        void setRevision(long revision) {
            this.revision = revision;
        }
    }

    private static class TestSnapshotLoader implements Supplier<DeliverySnapshot> {
        private final Supplier<DeliverySnapshot> supplier;

        TestSnapshotLoader(Supplier<DeliverySnapshot> supplier) {
            this.supplier = supplier;
        }

        @Override
        public DeliverySnapshot get() {
            return this.supplier.get();
        }
    }
}
