package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class SnapshotDeliveryRegistryTest {

    private ExecutorService reloadExecutor;

    @BeforeEach
    void setUp() {
        this.reloadExecutor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        this.reloadExecutor.shutdownNow();
    }

    private static DeliverySnapshot.DeliveryChannel simpleDc(long targetId) {
        return new DeliverySnapshot.DeliveryChannel(
                targetId, targetId, null, null,
                true, false, false, false, false,
                SeismicIntensity.ONE, "ja_jp", null
        );
    }

    /**
     * Test stub for DeliverySnapshotLoader that delegates to a Supplier.
     */
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

    /**
     * Stub ConfigRevisionStore that doesn't need a database.
     */
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

    @Nested
    @DisplayName("getDeliveryChannels() before initialization")
    class BeforeInitTests {

        @Test
        @DisplayName("should throw IllegalStateException when snapshot is not initialized")
        void throwsBeforeInit() {
            TestSnapshotLoader loader = new TestSnapshotLoader(() -> new DeliverySnapshot(1L, List.of()));
            StubRevisionStore revisionStore = new StubRevisionStore(0L);

            SnapshotDeliveryRegistry registry = new SnapshotDeliveryRegistry(loader, revisionStore, reloadExecutor);

            assertThatThrownBy(() -> registry.getDeliveryChannels(ChannelFilter.builder().build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Snapshot not initialized");
        }
    }

    @Nested
    @DisplayName("initializeSnapshot()")
    class InitTests {

        @Test
        @DisplayName("should load snapshot and make getDeliveryChannels() work")
        void initializeAndQuery() {
            TestSnapshotLoader loader = new TestSnapshotLoader(
                    () -> new DeliverySnapshot(1L, List.of(simpleDc(10L), simpleDc(20L))));
            StubRevisionStore revisionStore = new StubRevisionStore(1L);

            SnapshotDeliveryRegistry registry = new SnapshotDeliveryRegistry(loader, revisionStore, reloadExecutor);
            registry.initializeSnapshot();

            DeliveryPartition partition = registry.getDeliveryChannels(ChannelFilter.builder().build());
            assertThat(partition.direct()).hasSize(2);
        }

        @Test
        @DisplayName("getSnapshot() should return loaded snapshot after init")
        void getSnapshotAfterInit() {
            TestSnapshotLoader loader = new TestSnapshotLoader(
                    () -> new DeliverySnapshot(5L, List.of(simpleDc(1L))));
            StubRevisionStore revisionStore = new StubRevisionStore(5L);

            SnapshotDeliveryRegistry registry = new SnapshotDeliveryRegistry(loader, revisionStore, reloadExecutor);
            registry.initializeSnapshot();

            assertThat(registry.getSnapshot()).isNotNull();
            assertThat(registry.getSnapshot().getRevision()).isEqualTo(5L);
            assertThat(registry.getSnapshot().size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("requestReload()")
    class ReloadTests {

        @Test
        @DisplayName("should reload snapshot when revision changes")
        void reloadOnRevisionChange() throws Exception {
            CountDownLatch reloadDone = new CountDownLatch(1);
            AtomicInteger loadCount = new AtomicInteger(0);
            StubRevisionStore revisionStore = new StubRevisionStore(1L);

            TestSnapshotLoader loader = new TestSnapshotLoader(() -> {
                int count = loadCount.incrementAndGet();
                long rev = revisionStore.getRevision();
                if (rev == 2L) {
                    reloadDone.countDown();
                }
                return new DeliverySnapshot(rev, List.of(simpleDc(count)));
            });

            SnapshotDeliveryRegistry registry = new SnapshotDeliveryRegistry(loader, revisionStore, reloadExecutor);
            registry.initializeSnapshot();

            assertThat(registry.getSnapshot().getRevision()).isEqualTo(1L);

            // Simulate revision change and request reload
            revisionStore.setRevision(2L);
            registry.requestReload();

            // Wait for async reload
            assertThat(reloadDone.await(5, TimeUnit.SECONDS)).isTrue();

            // snapshotRef update may lag behind loader return; poll briefly
            long deadline = System.currentTimeMillis() + 2000;
            while (registry.getSnapshot().getRevision() < 2L && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertThat(registry.getSnapshot().getRevision()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should coalesce multiple reload requests into fewer loads")
        void coalesceReloads() throws Exception {
            CountDownLatch loadStarted = new CountDownLatch(1);
            CountDownLatch proceedLoad = new CountDownLatch(1);
            CountDownLatch allDone = new CountDownLatch(1);
            AtomicInteger loadCount = new AtomicInteger(0);
            StubRevisionStore revisionStore = new StubRevisionStore(1L);

            TestSnapshotLoader loader = new TestSnapshotLoader(() -> {
                int count = loadCount.incrementAndGet();
                if (count == 2) {
                    // Block the second load to allow coalescing test
                    loadStarted.countDown();
                    try {
                        proceedLoad.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                DeliverySnapshot snapshot = new DeliverySnapshot(revisionStore.getRevision(), List.of());
                // Signal completion once we've processed beyond the blocked load
                if (count > 2) {
                    allDone.countDown();
                }
                return snapshot;
            });

            SnapshotDeliveryRegistry registry = new SnapshotDeliveryRegistry(loader, revisionStore, reloadExecutor);
            registry.initializeSnapshot(); // loadCount = 1

            // Trigger first reload (will block in loader)
            revisionStore.setRevision(2L);
            registry.requestReload();

            // Wait for reload to be in progress
            loadStarted.await(5, TimeUnit.SECONDS);

            // Fire multiple reload requests while first is in progress
            // These should be coalesced since reloadScheduled is already true
            registry.requestReload();
            registry.requestReload();
            registry.requestReload();

            // Let the blocked load complete
            proceedLoad.countDown();

            // Wait for coalesced reloads to finish
            allDone.await(5, TimeUnit.SECONDS);
            // Small delay to let any additional loads settle
            Thread.sleep(100);

            // loadCount should be much less than 1 (init) + 1 (first reload) + 3 (all requests) = 5
            // Due to coalescing, extra requests are merged. Post-reload revision check
            // may trigger at most 1 more reload, so we expect <= 4 loads total
            assertThat(loadCount.get()).isLessThanOrEqualTo(4);
        }

        @Test
        @DisplayName("should not throw when executor is shut down")
        void requestReload_executorShutDown() {
            reloadExecutor.shutdownNow();
            StubRevisionStore revisionStore = new StubRevisionStore(1L);
            TestSnapshotLoader loader = new TestSnapshotLoader(() -> new DeliverySnapshot(1L, List.of()));
            SnapshotDeliveryRegistry registry = new SnapshotDeliveryRegistry(loader, revisionStore, reloadExecutor);

            assertThatCode(() -> registry.requestReload()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not update snapshot if loaded revision is not newer")
        void skipReloadWhenUpToDate() throws Exception {
            AtomicInteger loadCount = new AtomicInteger(0);
            StubRevisionStore revisionStore = new StubRevisionStore(1L);

            TestSnapshotLoader loader = new TestSnapshotLoader(() -> {
                loadCount.incrementAndGet();
                return new DeliverySnapshot(1L, List.of(simpleDc(1L)));
            });

            SnapshotDeliveryRegistry registry = new SnapshotDeliveryRegistry(loader, revisionStore, reloadExecutor);
            registry.initializeSnapshot();

            // Request reload without changing revision - snapshot should not change
            registry.requestReload();

            // Wait for async reload to complete
            Thread.sleep(200);

            assertThat(registry.getSnapshot().getRevision()).isEqualTo(1L);
        }
    }
}
