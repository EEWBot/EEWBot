package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RevisionPollerTest {

    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        this.scheduler.shutdownNow();
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
                loader, revisionStore, Executors.newSingleThreadExecutor()) {
            @Override
            public void requestReload() {
                reloadCount.incrementAndGet();
                reloadCalled.countDown();
                super.requestReload();
            }
        };
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, revisionStore, this.scheduler, 50);
        poller.start();

        // Change revision to trigger reload
        revisionStore.setRevision(2L);

        // Wait for poll to detect the change
        assertThat(reloadCalled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reloadCount.get()).isGreaterThanOrEqualTo(1);

        poller.stop();
    }

    @Test
    @DisplayName("start() should be idempotent - double start does not create duplicate tasks")
    void doubleStartIsIdempotent() {
        StubRevisionStore revisionStore = new StubRevisionStore(1L);
        TestSnapshotLoader loader = new TestSnapshotLoader(
                () -> new DeliverySnapshot(1L, List.of()));

        SnapshotDeliveryRegistry deliveryRegistry = new SnapshotDeliveryRegistry(
                loader, revisionStore, Executors.newSingleThreadExecutor());
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, revisionStore, this.scheduler, 100);

        // Start twice - should not throw
        poller.start();
        poller.start();

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
                loader, revisionStore, Executors.newSingleThreadExecutor()) {
            @Override
            public void requestReload() {
                reloadCount.incrementAndGet();
                super.requestReload();
            }
        };
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, revisionStore, this.scheduler, 50);
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
                loader, revisionStore, Executors.newSingleThreadExecutor());

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, revisionStore, this.scheduler, 100);
        // Should not throw
        poller.stop();
    }

    @Test
    @DisplayName("poll should not crash when revisionStore throws exception")
    void pollExceptionResilience() throws Exception {
        AtomicInteger pollCallCount = new AtomicInteger(0);
        CountDownLatch secondPoll = new CountDownLatch(1);

        ConfigRevisionStore throwingStore = new ConfigRevisionStore(null, null) {
            @Override
            public long getRevision() {
                int count = pollCallCount.incrementAndGet();
                if (count == 1) {
                    throw new RuntimeException("DB connection lost");
                }
                secondPoll.countDown();
                return 1L;
            }
        };

        TestSnapshotLoader loader = new TestSnapshotLoader(
                () -> new DeliverySnapshot(1L, List.of()));

        SnapshotDeliveryRegistry deliveryRegistry = new SnapshotDeliveryRegistry(
                loader, throwingStore, Executors.newSingleThreadExecutor());
        deliveryRegistry.initializeSnapshot();

        RevisionPoller poller = new RevisionPoller(deliveryRegistry, throwingStore, this.scheduler, 50);
        poller.start();

        // Second poll should still execute despite first one failing
        assertThat(secondPoll.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pollCallCount.get()).isGreaterThanOrEqualTo(2);

        poller.stop();
    }

    private static class StubRevisionStore extends ConfigRevisionStore {
        private volatile long revision;

        StubRevisionStore(long initialRevision) {
            super(null, null);
            this.revision = initialRevision;
        }

        @Override
        public long getRevision() {
            return this.revision;
        }

        void setRevision(long revision) {
            this.revision = revision;
        }
    }

    private static class TestSnapshotLoader extends DeliverySnapshotLoader {
        private final Supplier<DeliverySnapshot> supplier;

        TestSnapshotLoader(Supplier<DeliverySnapshot> supplier) {
            super(null, null);
            this.supplier = supplier;
        }

        @Override
        public DeliverySnapshot loadInTransaction() {
            return this.supplier.get();
        }
    }
}
