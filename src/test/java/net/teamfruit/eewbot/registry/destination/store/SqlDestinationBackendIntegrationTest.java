package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationBackend;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class SqlDestinationBackendIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void backendReopensExistingDatabaseAndDeliverySurvivesStorageShutdown() throws Exception {
        ConfigV2.Database config = new ConfigV2.Database();
        config.getSqlite().setPath("existing.db");
        long target = 1234567890123456789L;
        try (SqlDestinationBackend backend = SqlDestinationBackend.open(config, this.directory)) {
            backend.createAdminRegistry(() -> {}).put(target, Channel.createDefault(null, target, null, "en_us"));
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (DestinationBackend backend = SqlDestinationBackend.open(config, this.directory)) {
            SnapshotDeliveryRegistry delivery = new SnapshotDeliveryRegistry(backend.getConfigSource(), executor);
            delivery.initializeSnapshot();
            assertThat(backend.getConfigSource().getRevision()).isEqualTo(1);
            assertThat(delivery.getSnapshot().get(target).lang()).isEqualTo("en_us");

            backend.close();
            backend.close();

            assertThatThrownBy(() -> backend.getConfigSource().getRevision()).isInstanceOf(RuntimeException.class);
            assertThat(delivery.getDeliveryChannels(null).direct()).containsOnlyKeys(target);
        } finally {
            executor.shutdownNow();
        }
    }
}
