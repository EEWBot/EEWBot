package net.teamfruit.eewbot.registry.destination;

import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;

/**
 * Backend-independent source of delivery configuration. Called only at startup
 * and during background refresh, never when selecting delivery destinations.
 */
public interface DestinationConfigSource {

    /** Latest committed configuration revision, used as a refresh hint. */
    long getRevision();

    /**
     * Load an immutable snapshot whose revision and destinations describe the same
     * logical state. Implementations own read consistency; callers must use the
     * returned revision, rather than attaching an earlier getRevision() result.
     * Failures must throw, leaving the caller's previous snapshot intact.
     */
    DeliverySnapshot loadSnapshot();
}
