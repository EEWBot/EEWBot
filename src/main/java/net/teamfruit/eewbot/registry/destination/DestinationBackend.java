package net.teamfruit.eewbot.registry.destination;

/** Owns storage resources, independently of snapshot/executor lifecycles. */
public interface DestinationBackend extends AutoCloseable {

    DestinationConfigSource getConfigSource();

    /** The callback runs after a committed write affecting at least one row. */
    DestinationAdminRegistry createAdminRegistry(Runnable onWrite);

    /** Release storage resources. Repeated calls are safe. */
    @Override
    void close();
}
