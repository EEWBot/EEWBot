package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests: writes targeting non-existent rows must be no-ops
 * (no revision increment, no onWrite callback).
 */
class SqlAdminRegistryNonExistentTargetTest {

    @TempDir
    Path tempDir;

    private ChannelRegistrySql delegate;
    private ConfigRevisionStore revisionStore;
    private AtomicInteger onWriteCount;
    private SqlAdminRegistry adminRegistry;

    @BeforeEach
    void setUp() throws IOException {
        Path dbPath = this.tempDir.resolve("test.db");
        this.delegate = ChannelRegistrySql.forSQLite(dbPath);
        DatabaseInitializer.migrate(this.delegate.getDataSource(), SQLDialect.SQLITE);
        this.revisionStore = new ConfigRevisionStore(this.delegate.getDsl(), SQLDialect.SQLITE);
        this.onWriteCount = new AtomicInteger(0);
        this.adminRegistry = new SqlAdminRegistry(this.delegate, this.revisionStore, this.onWriteCount::incrementAndGet);
    }

    @AfterEach
    void tearDown() {
        if (this.delegate != null) {
            this.delegate.close();
        }
    }

    private static final long NON_EXISTENT = 999999L;

    @Nested
    @DisplayName("Non-existent target writes are no-ops")
    class NonExistentTargetTests {

        @Test
        @DisplayName("remove() on non-existent target is a no-op")
        void removeNonExistentIsNoop() {
            adminRegistry.remove(NON_EXISTENT);

            assertThat(revisionStore.getRevision()).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("set() on non-existent target is a no-op")
        void setNonExistentIsNoop() {
            adminRegistry.set(NON_EXISTENT, "eewAlert", true);

            assertThat(revisionStore.getRevision()).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("setAll() on non-existent target is a no-op")
        void setAllNonExistentIsNoop() {
            adminRegistry.setAll(NON_EXISTENT, Map.of("eewAlert", true, "quakeInfo", false));

            assertThat(revisionStore.getRevision()).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("setMinIntensity() on non-existent target is a no-op")
        void setMinIntensityNonExistentIsNoop() {
            adminRegistry.setMinIntensity(NON_EXISTENT, SeismicIntensity.FIVE_MINUS);

            assertThat(revisionStore.getRevision()).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("setWebhook() on non-existent target is a no-op")
        void setWebhookNonExistentIsNoop() {
            adminRegistry.setWebhook(NON_EXISTENT, ChannelWebhook.of(555L, "token"));

            assertThat(revisionStore.getRevision()).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("setLang() on non-existent target is a no-op")
        void setLangNonExistentIsNoop() {
            adminRegistry.setLang(NON_EXISTENT, "en_us");

            assertThat(revisionStore.getRevision()).isZero();
            assertThat(onWriteCount.get()).isZero();
        }
    }

    @Nested
    @DisplayName("Positive controls and edge cases")
    class PositiveControlTests {

        @Test
        @DisplayName("put() new key increments revision and fires onWrite")
        void putNewKeyDoesIncrement() {
            Channel channel = Channel.createDefault(100L, 1L, null, "ja_jp");
            adminRegistry.put(1L, channel);

            assertThat(revisionStore.getRevision()).isEqualTo(1L);
            assertThat(onWriteCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("put() existing key (onConflictDoNothing) is a no-op")
        void putExistingKeyIsNoop() {
            Channel channel = Channel.createDefault(100L, 1L, null, "ja_jp");
            adminRegistry.put(1L, channel);
            onWriteCount.set(0);
            long revisionBefore = revisionStore.getRevision();

            // Second put with same key — onConflictDoNothing → 0 rows affected
            Channel channel2 = Channel.createDefault(200L, 2L, null, "en_us");
            adminRegistry.put(1L, channel2);

            assertThat(revisionStore.getRevision()).isEqualTo(revisionBefore);
            assertThat(onWriteCount.get()).isZero();
            // Original data should be unchanged
            assertThat(adminRegistry.get(1L).getGuildId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Same-value update on existing row still increments revision (affected > 0)")
        void sameValueUpdateDoesIncrement() {
            Channel channel = Channel.createDefault(100L, 1L, null, "ja_jp");
            adminRegistry.put(1L, channel);
            onWriteCount.set(0);
            long revisionBefore = revisionStore.getRevision();

            // Set same lang value — UPDATE matches the row (affected=1), so revision++
            adminRegistry.setLang(1L, "ja_jp");

            assertThat(revisionStore.getRevision()).isEqualTo(revisionBefore + 1);
            assertThat(onWriteCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Webhook invalid URL via admin registry")
    class WebhookInvalidUrlTests {

        @Test
        @DisplayName("clearWebhookByUrls() with all invalid URLs is a no-op")
        void clearWebhookByAllInvalidUrls_noRevisionNoOnWrite() {
            // Insert a channel with a valid webhook so the DB is not empty
            Channel ch = new Channel(100L, 1L, null, true, false, false, false,
                    false, SeismicIntensity.ONE, ChannelWebhook.of(555L, "tok"), "ja_jp");
            adminRegistry.put(1L, ch);
            onWriteCount.set(0);
            long revisionBefore = revisionStore.getRevision();

            int cleared = adminRegistry.clearWebhookByUrls(List.of("not-a-url", "garbage"));

            assertThat(cleared).isZero();
            assertThat(revisionStore.getRevision()).isEqualTo(revisionBefore);
            assertThat(onWriteCount.get()).isZero();
            // Webhook should remain untouched
            assertThat(adminRegistry.get(1L).getWebhook()).isNotNull();
        }
    }
}
