package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Delivery registry backed by an immutable in-memory snapshot.
 * All reads are zero-DB-access. Snapshot is reloaded asynchronously when notified.
 */
public class SnapshotDeliveryRegistry implements DestinationDeliveryRegistry {

    private final DeliverySnapshotLoader snapshotLoader;
    private final ConfigRevisionStore revisionStore;
    private final ExecutorService reloadExecutor;
    private final AtomicReference<DeliverySnapshot> snapshotRef;
    private final AtomicBoolean reloadScheduled = new AtomicBoolean(false);

    public SnapshotDeliveryRegistry(
            DeliverySnapshotLoader snapshotLoader,
            ConfigRevisionStore revisionStore,
            ExecutorService reloadExecutor
    ) {
        this.snapshotLoader = snapshotLoader;
        this.revisionStore = revisionStore;
        this.reloadExecutor = reloadExecutor;
        this.snapshotRef = new AtomicReference<>();
    }

    /**
     * Initialize the snapshot. Must be called before using delivery path methods.
     * Blocks until snapshot is loaded. Throws exception on failure (fail-fast).
     */
    public void initializeSnapshot() {
        Log.logger.info("Initializing delivery snapshot...");
        DeliverySnapshot snapshot = this.snapshotLoader.loadInTransaction();
        this.snapshotRef.set(snapshot);
        Log.logger.info("Delivery snapshot initialized: {} channels, revision {}",
                snapshot.size(), snapshot.getRevision());
    }

    /**
     * Get the current snapshot (for RevisionPoller).
     */
    public DeliverySnapshot getSnapshot() {
        return this.snapshotRef.get();
    }

    /**
     * Request a snapshot reload. Coalesces multiple requests into one.
     * Used by both SqlAdminRegistry writes and RevisionPoller.
     */
    public void requestReload() {
        if (this.reloadScheduled.compareAndSet(false, true)) {
            this.reloadExecutor.execute(this::reloadTask);
        }
    }

    private void reloadTask() {
        try {
            reloadSnapshotLoop();
        } catch (Exception e) {
            Log.logger.error("Error reloading snapshot", e);
        } finally {
            this.reloadScheduled.set(false);
        }
        // Check if another reload is needed after completion
        try {
            long latest = this.revisionStore.getRevision();
            DeliverySnapshot cur = this.snapshotRef.get();
            if (cur == null || cur.getRevision() < latest) {
                requestReload();
            }
        } catch (Exception e) {
            Log.logger.error("Error checking revision after reload", e);
        }
    }

    private void reloadSnapshotLoop() {
        DeliverySnapshot current = this.snapshotRef.get();
        DeliverySnapshot loaded = this.snapshotLoader.loadInTransaction();
        if (current != null && current.getRevision() >= loaded.getRevision()) {
            return; // Already up to date
        }
        this.snapshotRef.set(loaded);
        Log.logger.debug("Snapshot reloaded: {} channels, revision {}",
                loaded.size(), loaded.getRevision());
    }

    // ========================================
    // Delivery path methods - use snapshot only
    // ========================================

    @Override
    public DeliveryPartition getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        DeliverySnapshot snapshot = this.snapshotRef.get();
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot not initialized. Call initializeSnapshot() first.");
        }
        return snapshot.getPartitionedByWebhook(filter);
    }
}
