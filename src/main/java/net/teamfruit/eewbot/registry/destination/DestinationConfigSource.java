package net.teamfruit.eewbot.registry.destination;

import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;

/**
 * Backend-independent source of delivery configuration. Called only at startup
 * and during background refresh, never when selecting delivery destinations.
 */
public interface DestinationConfigSource {

    /** Monotonically increasing committed revision, used as a refresh hint. */
    long getRevision();

    /**
     * Load immutable, committed delivery settings with a revision used for refresh.
     * Settings may be newer than the returned revision, for example when a write
     * commits between reading the revision and reading the destinations. The
     * revision must not be ahead of the settings: this could hide a needed reload.
     * Once writes stop, successful reads must eventually catch up to the latest
     * committed revision. Polling and the post-reload check then ensure convergence.
     * Callers must keep the source's returned revision instead of substituting a
     * separately read getRevision() result.
     * Failures must throw, leaving the caller's previous snapshot intact.
     */
    DeliverySnapshot loadSnapshot();
}
