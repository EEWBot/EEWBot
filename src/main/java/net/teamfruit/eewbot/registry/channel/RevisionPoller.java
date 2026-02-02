package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.Log;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls the config_meta.channels_revision periodically to detect external changes.
 * When a change is detected, triggers snapshot reload via ChannelRegistryCached.
 */
public class RevisionPoller {

    private static final long DEFAULT_POLL_INTERVAL_MS = 2000;

    private final ChannelRegistryCached cachedRegistry;
    private final ConfigRevisionStore revisionStore;
    private final ScheduledExecutorService scheduler;
    private final long pollIntervalMs;
    private ScheduledFuture<?> pollTask;

    public RevisionPoller(
            ChannelRegistryCached cachedRegistry,
            ConfigRevisionStore revisionStore,
            ScheduledExecutorService scheduler
    ) {
        this(cachedRegistry, revisionStore, scheduler, DEFAULT_POLL_INTERVAL_MS);
    }

    public RevisionPoller(
            ChannelRegistryCached cachedRegistry,
            ConfigRevisionStore revisionStore,
            ScheduledExecutorService scheduler,
            long pollIntervalMs
    ) {
        this.cachedRegistry = cachedRegistry;
        this.revisionStore = revisionStore;
        this.scheduler = scheduler;
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * Start the revision polling.
     */
    public void start() {
        if (this.pollTask != null && !this.pollTask.isCancelled()) {
            Log.logger.warn("RevisionPoller already started");
            return;
        }
        this.pollTask = this.scheduler.scheduleAtFixedRate(
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
            long dbRevision = this.revisionStore.getRevision();
            DeliverySnapshot current = this.cachedRegistry.getSnapshot();
            if (current == null || current.getRevision() < dbRevision) {
                Log.logger.debug("Revision change detected: current={}, db={}",
                        current != null ? current.getRevision() : "null", dbRevision);
                this.cachedRegistry.requestReload();
            }
        } catch (Exception e) {
            Log.logger.error("Error in revision poll", e);
        }
    }
}
