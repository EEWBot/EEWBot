package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationBackend;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns only remote transport and its dedicated polling scheduler; never opens SQL. */
public final class RemoteDestinationBackend implements DestinationBackend {
    private final RemoteConfigApiClient client;
    private final DestinationConfigSource source;
    private final ScheduledExecutorService pollScheduler;
    private final long pollIntervalMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    private RemoteDestinationBackend(ConfigV2.Remote config, RemoteConfigApiClient client) {
        this.client = client;
        this.source = new RemoteDestinationConfigSource(client);
        this.pollIntervalMillis = config.getPollIntervalMillis();
        this.pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "eewbot-remote-poll"));
    }

    public static RemoteDestinationBackend open(ConfigV2.Remote config) {
        return initialize(config, new RemoteConfigApiClient(config,
                System.getenv("EEWBOT_CF_ACCESS_CLIENT_ID"), System.getenv("EEWBOT_CF_ACCESS_CLIENT_SECRET")));
    }

    // Takes ownership before compatibility discovery, including failure.
    static RemoteDestinationBackend initialize(ConfigV2.Remote config, RemoteConfigApiClient client) {
        try {
            client.checkCompatibility();
            return new RemoteDestinationBackend(config, client);
        } catch (RuntimeException | Error e) {
            client.close();
            throw e;
        }
    }

    @Override public DestinationConfigSource getConfigSource() { return this.source; }
    public ScheduledExecutorService getPollScheduler() { return this.pollScheduler; }
    public long getPollIntervalMillis() { return this.pollIntervalMillis; }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.pollScheduler.shutdownNow();
            this.client.close();
        }
    }
}
