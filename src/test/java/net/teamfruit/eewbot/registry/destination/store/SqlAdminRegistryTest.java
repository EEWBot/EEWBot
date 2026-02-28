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

class SqlAdminRegistryTest {

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

    private Channel defaultChannel() {
        return Channel.createDefault(100L, 1L, null, "ja_jp");
    }

    @Nested
    @DisplayName("Single write methods trigger onWrite")
    class SingleWriteTests {

        @Test
        @DisplayName("put() should trigger onWrite and increment revision")
        void putTriggersOnWrite() {
            adminRegistry.put(1L, defaultChannel());

            assertThat(onWriteCount.get()).isEqualTo(1);
            assertThat(revisionStore.getRevision()).isEqualTo(1L);
            assertThat(adminRegistry.exists(1L)).isTrue();
        }

        @Test
        @DisplayName("remove() should trigger onWrite and increment revision")
        void removeTriggersOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            adminRegistry.remove(1L);

            assertThat(onWriteCount.get()).isEqualTo(1);
            assertThat(adminRegistry.exists(1L)).isFalse();
        }

        @Test
        @DisplayName("set() should trigger onWrite and increment revision")
        void setTriggersOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            adminRegistry.set(1L, "eewAlert", true);

            assertThat(onWriteCount.get()).isEqualTo(1);
            assertThat(adminRegistry.get(1L).isEewAlert()).isTrue();
        }

        @Test
        @DisplayName("setAll() should trigger onWrite")
        void setAllTriggersOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            adminRegistry.setAll(1L, Map.of("eewAlert", true, "quakeInfo", true));

            assertThat(onWriteCount.get()).isEqualTo(1);
            Channel ch = adminRegistry.get(1L);
            assertThat(ch.isEewAlert()).isTrue();
            assertThat(ch.isQuakeInfo()).isTrue();
        }

        @Test
        @DisplayName("setAll() with empty map should not trigger onWrite")
        void setAllEmptyNoOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            adminRegistry.setAll(1L, Map.of());

            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("setMinIntensity() should trigger onWrite")
        void setMinIntensityTriggersOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            adminRegistry.setMinIntensity(1L, SeismicIntensity.FIVE_MINUS);

            assertThat(onWriteCount.get()).isEqualTo(1);
            assertThat(adminRegistry.get(1L).getMinIntensity()).isEqualTo(SeismicIntensity.FIVE_MINUS);
        }

        @Test
        @DisplayName("setWebhook() should trigger onWrite")
        void setWebhookTriggersOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            ChannelWebhook webhook = ChannelWebhook.of(555L, "token123");
            adminRegistry.setWebhook(1L, webhook);

            assertThat(onWriteCount.get()).isEqualTo(1);
            assertThat(adminRegistry.get(1L).getWebhook()).isNotNull();
            assertThat(adminRegistry.get(1L).getWebhook().id()).isEqualTo(555L);
        }

        @Test
        @DisplayName("setLang() should trigger onWrite")
        void setLangTriggersOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            adminRegistry.setLang(1L, "en_us");

            assertThat(onWriteCount.get()).isEqualTo(1);
            assertThat(adminRegistry.get(1L).getLang()).isEqualTo("en_us");
        }

    }

    @Nested
    @DisplayName("Batch write methods")
    class BatchWriteTests {

        @Test
        @DisplayName("removeByGuildId() should trigger onWrite only when rows removed")
        void removeByGuildIdTriggersOnWrite() {
            adminRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
            adminRegistry.put(2L, Channel.createDefault(100L, 2L, null, "ja_jp"));
            adminRegistry.put(3L, Channel.createDefault(200L, 3L, null, "ja_jp"));
            onWriteCount.set(0);

            int removed = adminRegistry.removeByGuildId(100L);

            assertThat(removed).isEqualTo(2);
            assertThat(onWriteCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("removeByGuildId() with no matches should not trigger onWrite")
        void removeByGuildIdNoMatchNoOnWrite() {
            adminRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
            onWriteCount.set(0);

            int removed = adminRegistry.removeByGuildId(999L);

            assertThat(removed).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("clearWebhookByUrls() should trigger onWrite only when rows updated")
        void clearWebhookByUrlsTriggersOnWrite() {
            Channel ch = new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, ChannelWebhook.of(555L, "tok"), "ja_jp");
            adminRegistry.put(1L, ch);
            onWriteCount.set(0);

            int cleared = adminRegistry.clearWebhookByUrls(List.of("https://discord.com/api/webhooks/555/tok"));

            assertThat(cleared).isEqualTo(1);
            assertThat(onWriteCount.get()).isEqualTo(1);
            assertThat(adminRegistry.get(1L).getWebhook()).isNull();
        }

        @Test
        @DisplayName("clearWebhookByUrls() with empty list should not trigger onWrite")
        void clearWebhookByUrlsEmptyNoOnWrite() {
            int cleared = adminRegistry.clearWebhookByUrls(List.of());

            assertThat(cleared).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("clearWebhookByUrls() with no matches should not trigger onWrite")
        void clearWebhookByUrlsNoMatchNoOnWrite() {
            adminRegistry.put(1L, defaultChannel());
            onWriteCount.set(0);

            int cleared = adminRegistry.clearWebhookByUrls(List.of("https://discord.com/api/webhooks/999/tok"));

            assertThat(cleared).isZero();
            assertThat(onWriteCount.get()).isZero();
        }

        @Test
        @DisplayName("setLangByGuildId() should trigger onWrite only when rows updated")
        void setLangByGuildIdTriggersOnWrite() {
            adminRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
            adminRegistry.put(2L, Channel.createDefault(100L, 2L, null, "ja_jp"));
            onWriteCount.set(0);

            int updated = adminRegistry.setLangByGuildId(100L, "en_us");

            assertThat(updated).isEqualTo(2);
            assertThat(onWriteCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("setLangByGuildId() with no matches should not trigger onWrite")
        void setLangByGuildIdNoMatchNoOnWrite() {
            adminRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
            onWriteCount.set(0);

            int updated = adminRegistry.setLangByGuildId(999L, "en_us");

            assertThat(updated).isZero();
            assertThat(onWriteCount.get()).isZero();
        }
    }

    @Nested
    @DisplayName("Read methods delegate correctly")
    class ReadDelegationTests {

        @Test
        @DisplayName("get() should delegate to underlying registry")
        void getDelegate() {
            adminRegistry.put(1L, defaultChannel());
            Channel ch = adminRegistry.get(1L);
            assertThat(ch).isNotNull();
            assertThat(ch.getGuildId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("exists() should delegate to underlying registry")
        void existsDelegate() {
            assertThat(adminRegistry.exists(1L)).isFalse();
            adminRegistry.put(1L, defaultChannel());
            assertThat(adminRegistry.exists(1L)).isTrue();
        }

        @Test
        @DisplayName("getAllChannels() should delegate to underlying registry")
        void getAllChannelsDelegate() {
            adminRegistry.put(1L, defaultChannel());
            adminRegistry.put(2L, Channel.createDefault(200L, 2L, null, "ja_jp"));

            Map<Long, Channel> all = adminRegistry.getAllChannels();
            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("getDelegate() should return the underlying ChannelRegistrySql")
        void getDelegateReturnsUnderlying() {
            assertThat(adminRegistry.getDelegate()).isSameAs(delegate);
        }
    }

    @Nested
    @DisplayName("Revision atomicity")
    class RevisionAtomicityTests {

        @Test
        @DisplayName("multiple writes should increment revision correctly")
        void multipleWritesIncrementRevision() {
            adminRegistry.put(1L, defaultChannel());
            adminRegistry.set(1L, "eewAlert", true);
            adminRegistry.setLang(1L, "en_us");

            // 3 writes = 3 revision increments
            assertThat(revisionStore.getRevision()).isEqualTo(3L);
        }
    }
}
