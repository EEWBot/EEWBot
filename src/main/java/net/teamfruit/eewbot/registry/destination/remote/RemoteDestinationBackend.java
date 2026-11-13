package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationBackend;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.DestinationCleanup;

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
    private final RemoteDestinationCleanup cleanup;

    private RemoteDestinationBackend(ConfigV2.Remote config, RemoteConfigApiClient client, RemoteCleanupApiClient cleanupClient) {
        this.client = client;
        this.source = new RemoteDestinationConfigSource(client);
        this.pollIntervalMillis = config.getPollIntervalMillis();
        this.cleanup = cleanupClient == null ? null : new RemoteDestinationCleanup(cleanupClient);
        this.pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "eewbot-remote-poll"));
    }

    public static RemoteDestinationBackend open(ConfigV2.Remote config) {
        return initialize(config, new RemoteConfigApiClient(config,
                System.getenv("EEWBOT_CF_ACCESS_CLIENT_ID"), System.getenv("EEWBOT_CF_ACCESS_CLIENT_SECRET")));
    }

    // Takes ownership before the startup probe, including failure.
    static RemoteDestinationBackend initialize(ConfigV2.Remote config, RemoteConfigApiClient client) {
        return initialize(config, client, () -> new RemoteCleanupApiClient(config,
                System.getenv("EEWBOT_CF_ACCESS_CLIENT_ID"), System.getenv("EEWBOT_CF_ACCESS_CLIENT_SECRET")));
    }

    static RemoteDestinationBackend initialize(ConfigV2.Remote config, RemoteConfigApiClient client,
                                               java.util.function.Supplier<RemoteCleanupApiClient> cleanupFactory) {
        RemoteCleanupApiClient cleanupClient = null;
        try {
            // Fail startup on unreachable or unauthorized Access before connecting to Discord.
            // The writer shares these credentials and has no side-effect-free endpoint to probe.
            client.revision();
            if (config.isCleanupEnabled()) cleanupClient = cleanupFactory.get();
            return new RemoteDestinationBackend(config, client, cleanupClient);
        } catch (RuntimeException | Error e) {
            if (cleanupClient != null) cleanupClient.close();
            client.close();
            throw e;
        }
    }

    @Override public DestinationConfigSource getConfigSource() { return this.source; }
    public ScheduledExecutorService getPollScheduler() { return this.pollScheduler; }
    public long getPollIntervalMillis() { return this.pollIntervalMillis; }
    public DestinationCleanup getCleanup() {
        return this.cleanup == null ? new RemoteReadOnlyDestinationCleanup() : this.cleanup;
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            if (this.cleanup != null) this.cleanup.close();
            this.pollScheduler.shutdownNow();
            this.client.close();
        }
    }
}
