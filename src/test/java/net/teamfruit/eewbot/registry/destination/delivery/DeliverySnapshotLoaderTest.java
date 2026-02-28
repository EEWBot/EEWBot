package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for DeliverySnapshotLoader using real SQLite DB (no stubs).
 */
class DeliverySnapshotLoaderTest {

    @TempDir
    Path tempDir;

    private ChannelRegistrySql sqlRegistry;
    private ConfigRevisionStore revisionStore;
    private DeliverySnapshotLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        Path dbPath = this.tempDir.resolve("test.db");
        this.sqlRegistry = ChannelRegistrySql.forSQLite(dbPath);
        DatabaseInitializer.migrate(this.sqlRegistry.getDataSource(), SQLDialect.SQLITE);
        this.revisionStore = new ConfigRevisionStore(this.sqlRegistry.getDsl(), SQLDialect.SQLITE);
        this.loader = new DeliverySnapshotLoader(this.sqlRegistry, this.revisionStore);
    }

    @AfterEach
    void tearDown() {
        if (this.sqlRegistry != null) {
            this.sqlRegistry.close();
        }
    }

    @Test
    @DisplayName("Empty DB returns snapshot with revision=0 and size=0")
    void emptyDbReturnsZero() {
        DeliverySnapshot snapshot = this.loader.loadInTransaction();

        assertThat(snapshot.getRevision()).isZero();
        assertThat(snapshot.size()).isZero();
    }

    @Test
    @DisplayName("Snapshot revision and channel count match DB state after writes")
    void consistentRevisionAndChannels() {
        // Insert 3 channels, incrementing revision each time
        this.sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
        this.revisionStore.incrementRevision();
        this.sqlRegistry.put(2L, Channel.createDefault(100L, 2L, null, "ja_jp"));
        this.revisionStore.incrementRevision();
        this.sqlRegistry.put(3L, Channel.createDefault(200L, 3L, null, "en_us"));
        this.revisionStore.incrementRevision();

        DeliverySnapshot snapshot = this.loader.loadInTransaction();

        assertThat(snapshot.getRevision()).isEqualTo(3L);
        assertThat(snapshot.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("After additional write, reload returns updated snapshot")
    void afterWriteReturnsUpdated() {
        this.sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
        this.revisionStore.incrementRevision();

        DeliverySnapshot snapshot1 = this.loader.loadInTransaction();
        assertThat(snapshot1.getRevision()).isEqualTo(1L);
        assertThat(snapshot1.size()).isEqualTo(1);

        // Additional write
        this.sqlRegistry.put(2L, Channel.createDefault(100L, 2L, null, "en_us"));
        this.revisionStore.incrementRevision();

        DeliverySnapshot snapshot2 = this.loader.loadInTransaction();
        assertThat(snapshot2.getRevision()).isEqualTo(2L);
        assertThat(snapshot2.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Snapshot field values match DB values")
    void snapshotFieldsMatchDb() {
        ChannelWebhook webhook = ChannelWebhook.of(555L, "myToken");
        Channel ch = new Channel(100L, 1L, 999L, true, false, true, false,
                SeismicIntensity.FOUR, webhook, "en_us");
        this.sqlRegistry.put(999L, ch);
        this.revisionStore.incrementRevision();

        DeliverySnapshot snapshot = this.loader.loadInTransaction();
        assertThat(snapshot.size()).isEqualTo(1);

        DeliverySnapshot.DeliveryChannel dc = snapshot.get(999L);
        assertThat(dc.targetId()).isEqualTo(999L);
        assertThat(dc.channelId()).isEqualTo(1L);
        assertThat(dc.threadId()).isEqualTo(999L);
        assertThat(dc.guildId()).isEqualTo(100L);
        assertThat(dc.eewAlert()).isTrue();
        assertThat(dc.eewPrediction()).isFalse();
        assertThat(dc.eewDecimation()).isTrue();
        assertThat(dc.quakeInfo()).isFalse();
        assertThat(dc.minIntensity()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(dc.lang()).isEqualTo("en_us");
        assertThat(dc.webhook()).isNotNull();
        assertThat(dc.webhook().id()).isEqualTo(555L);
    }
}
