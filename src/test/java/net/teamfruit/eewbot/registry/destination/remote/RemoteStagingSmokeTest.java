package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in: fixed staging origin, read-only API, no Discord login or local database. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "EEWBOT_REMOTE_STAGING_SMOKE", matches = "true")
class RemoteStagingSmokeTest {
    @Test void stagingEmptySnapshotAndRealRevisionPolling() throws Exception {
        var config = Codecs.GSON.fromJson("{\"baseUrl\":\"https://api-staging.eewbot.net\"}", ConfigV2.Remote.class);
        try (var backend = RemoteDestinationBackend.open(config);
             var reload = Executors.newSingleThreadExecutor()) {
            var polled = new CountDownLatch(1);
            var remote = backend.getConfigSource();
            DestinationConfigSource observed = new DestinationConfigSource() {
                @Override public long getRevision() {
                    long revision = remote.getRevision();
                    assertThat(revision).isZero();
                    polled.countDown();
                    return revision;
                }
                @Override public DeliverySnapshot loadSnapshot() { return remote.loadSnapshot(); }
            };
            var registry = new SnapshotDeliveryRegistry(observed, reload);
            registry.initializeSnapshot();
            assertThat(registry.getSnapshot().getRevision()).isZero();
            assertThat(registry.getSnapshot().size()).isZero();
            var poller = new RevisionPoller(registry, observed, backend.getPollScheduler(), backend.getPollIntervalMillis(), true);
            try {
                poller.start();
                assertThat(polled.await(20, TimeUnit.SECONDS)).isTrue();
                assertThat(registry.getSnapshot().getRevision()).isZero();
                assertThat(registry.getDeliveryChannels(null).direct()).isEmpty();
                Log.logger.info("Staging remote backend verified: revision 0, destinations 0, HTTP revision polling successful");
            } finally {
                poller.stop();
            }
        }
    }
}
