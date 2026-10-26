package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in; fixed staging host, no Discord login; changed rows require a verified synthetic fixture. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "EEWBOT_REMOTE_CLEANUP_STAGING_SMOKE", matches = "true")
class RemoteCleanupStagingSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "EEWBOT_REMOTE_CLEANUP_STAGING_FIXTURE", matches = ".+")
    void stagingQueuedCleanupChangesD1AndPollsANewSnapshot() throws Exception {
        var fixture = new ObjectMapper().readTree(System.getenv("EEWBOT_REMOTE_CLEANUP_STAGING_FIXTURE"));
        var ids = new java.util.ArrayList<Long>();
        for (var id : fixture.get("ids")) ids.add(Long.parseLong(id.textValue()));
        assertThat(ids).hasSize(7).allSatisfy(id -> assertThat(id).isBetween(9223372036854000101L, 9223372036854000107L));
        long guild = Long.parseLong(fixture.get("guildId").textValue());
        assertThat(guild).isEqualTo(9223372036854000901L);
        assertThat(fixture.get("marker").textValue()).isEqualTo("x_cleanup_smoke_20261026");
        var config = Codecs.GSON.fromJson("{\"baseUrl\":\"https://api-staging.eewbot.net\",\"cleanupEnabled\":true,\"pollIntervalMillis\":100}", ConfigV2.Remote.class);
        try (var backend = RemoteDestinationBackend.open(config); var reload = Executors.newSingleThreadExecutor()) {
            var source = backend.getConfigSource();
            var registry = new SnapshotDeliveryRegistry(source, reload);
            registry.initializeSnapshot();
            var before = registry.getSnapshot();
            for (long id : ids) {
                var channel = before.get(id);
                assertThat(channel).isNotNull();
                assertThat(channel.lang()).isEqualTo("x_cleanup_smoke_20261026");
                assertThat(channel.eewAlert() || channel.eewPrediction() || channel.eewDecimation() || channel.quakeInfo() || channel.tsunami()).isFalse();
            }
            var cleanup = backend.getCleanup();
            cleanup.guildDeleted(guild);
            cleanup.destinationDeleted(ids.get(2));
            cleanup.destinationsUnavailable(List.of(ids.get(3)));
            cleanup.webhooksUnavailable(Map.of(ids.get(4), before.get(ids.get(4)).toDeliveryTarget(ids.get(4))));
            String sharedUrl = before.get(ids.get(5)).webhook().getUrl();
            assertThat(cleanup.webhookUrlsUnavailable(List.of(sharedUrl))).isEqualTo(2);
            assertThat(registry.getSnapshot()).isSameAs(before);
            long afterRevision = source.getRevision();
            assertThat(afterRevision).isEqualTo(before.getRevision() + 5);
            var poller = new RevisionPoller(registry, source, backend.getPollScheduler(), 100, true);
            try {
                poller.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (registry.getSnapshot().getRevision() != afterRevision && System.nanoTime() < deadline) Thread.sleep(25);
                assertThat(registry.getSnapshot().getRevision()).isEqualTo(afterRevision);
                for (int i = 0; i < 4; i++) assertThat(registry.getSnapshot().get(ids.get(i))).isNull();
                for (int i = 4; i < 7; i++) assertThat(registry.getSnapshot().get(ids.get(i)).webhook()).isNull();
            } finally { poller.stop(); }
        }
    }

    @Test void stagingCommonTokenCompatibilityAndNoopRevision() {
        var config = Codecs.GSON.fromJson("{\"baseUrl\":\"https://api-staging.eewbot.net\",\"cleanupEnabled\":true}", ConfigV2.Remote.class);
        String readId = System.getenv("EEWBOT_CF_ACCESS_CLIENT_ID");
        String readSecret = System.getenv("EEWBOT_CF_ACCESS_CLIENT_SECRET");
        try (var reader = new RemoteConfigApiClient(config, readId, readSecret);
             var writer = new RemoteCleanupApiClient(config, readId, readSecret)) {
            reader.checkCompatibility();
            writer.checkCompatibility();
            var source = new RemoteDestinationConfigSource(reader);
            long revision = source.getRevision();
            assertThat(writer.mutate("destinations", Map.of("targetIds", List.of()))).isZero();
            assertThat(writer.mutate("webhooks", Map.of("targets", List.of()))).isZero();
            assertThat(writer.mutate("webhook-ids", Map.of("webhookIds", List.of()))).isZero();
            assertThat(source.getRevision()).isEqualTo(revision);
        }
    }
}
