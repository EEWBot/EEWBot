package net.teamfruit.eewbot.registry.destination;

/** Owns storage resources, independently of snapshot/executor lifecycles. */
public interface DestinationBackend extends AutoCloseable {

    DestinationConfigSource getConfigSource();

    /** Release storage resources. Repeated calls are safe. */
    @Override
    void close();
}
