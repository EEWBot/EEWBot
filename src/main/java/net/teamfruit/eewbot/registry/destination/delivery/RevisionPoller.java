package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls the configuration revision periodically to detect external changes.
 * When a change is detected, triggers snapshot reload via SnapshotDeliveryRegistry.
 */
public class RevisionPoller {

    private static final long DEFAULT_POLL_INTERVAL_MS = 2000;

    private final SnapshotDeliveryRegistry snapshotDeliveryRegistry;
    private final DestinationConfigSource configSource;
    private final ScheduledExecutorService scheduler;
    private final long pollIntervalMs;
    private final boolean fixedDelay;
    private ScheduledFuture<?> pollTask;

    public RevisionPoller(
            SnapshotDeliveryRegistry snapshotDeliveryRegistry,
            DestinationConfigSource configSource,
            ScheduledExecutorService scheduler
    ) {
        this(snapshotDeliveryRegistry, configSource, scheduler, DEFAULT_POLL_INTERVAL_MS);
    }

    public RevisionPoller(
            SnapshotDeliveryRegistry snapshotDeliveryRegistry,
            DestinationConfigSource configSource,
            ScheduledExecutorService scheduler,
            long pollIntervalMs
    ) {
        this(snapshotDeliveryRegistry, configSource, scheduler, pollIntervalMs, false);
    }

    public RevisionPoller(SnapshotDeliveryRegistry snapshotDeliveryRegistry, DestinationConfigSource configSource,
                          ScheduledExecutorService scheduler, long pollIntervalMs, boolean fixedDelay) {
        this.snapshotDeliveryRegistry = snapshotDeliveryRegistry;
        this.configSource = configSource;
        this.scheduler = scheduler;
        this.pollIntervalMs = pollIntervalMs;
        this.fixedDelay = fixedDelay;
    }

    /**
     * Start the revision polling.
     */
    public void start() {
        if (this.pollTask != null && !this.pollTask.isCancelled()) {
            Log.logger.warn("RevisionPoller already started");
            return;
        }
        this.pollTask = this.fixedDelay ? this.scheduler.scheduleWithFixedDelay(
                this::poll, this.pollIntervalMs, this.pollIntervalMs, TimeUnit.MILLISECONDS
        ) : this.scheduler.scheduleAtFixedRate(
                this::poll,
                this.pollIntervalMs,
                this.pollIntervalMs,
                TimeUnit.MILLISECONDS
        );
        Log.logger.info("RevisionPoller started with interval {} ms", this.pollIntervalMs);
    }

    /**
     * Stop the revision polling.
     */
    public void stop() {
        if (this.pollTask != null) {
            this.pollTask.cancel(false);
            this.pollTask = null;
            Log.logger.info("RevisionPoller stopped");
        }
    }

    private void poll() {
        try {
            long sourceRevision = this.configSource.getRevision();
            DeliverySnapshot current = this.snapshotDeliveryRegistry.getSnapshot();
            if (current == null || current.getRevision() < sourceRevision) {
                Log.logger.debug("Revision change detected: current={}, source={}",
                        current != null ? current.getRevision() : "null", sourceRevision);
                this.snapshotDeliveryRegistry.requestReload();
            }
        } catch (Exception e) {
            Log.logger.error("Error in revision poll", e);
        }
    }
}
