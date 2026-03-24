package net.teamfruit.eewbot.registry.destination.migration;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ChannelMigrationTest {

    @TempDir
    Path tempDir;

    private ChannelRegistrySql destination;

    @BeforeEach
    void setUp() throws IOException {
        Path dbPath = this.tempDir.resolve("migration_test.db");
        this.destination = ChannelRegistrySql.forSQLite(dbPath);
        DatabaseInitializer.migrate(this.destination.getDataSource(), SQLDialect.SQLITE);
    }

    @AfterEach
    void tearDown() {
        if (this.destination != null) {
            this.destination.close();
        }
    }

    @Nested
    @DisplayName("migrateChannelsSql() targetId/channelId decision logic")
    class TargetIdDecisionTests {

        @Test
        @DisplayName("channel with channelId and no threadId: targetId = channelId")
        void channelWithChannelIdNoThread() {
            Channel ch = new Channel(100L, 500L, null, true, false, false, false,
                    false, SeismicIntensity.ONE, null, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(500L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            assertThat(destination.exists(500L)).isTrue();
            Channel result = destination.get(500L);
            assertThat(result.getChannelId()).isEqualTo(500L);
            assertThat(result.getThreadId()).isNull();
        }

        @Test
        @DisplayName("channel with threadId: targetId = threadId")
        void channelWithThreadId() {
            Channel ch = new Channel(100L, 500L, 999L, true, false, false, false,
                    false, SeismicIntensity.ONE, null, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(500L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            // targetId should be threadId (999), not the original key (500)
            assertThat(destination.exists(999L)).isTrue();
            Channel result = destination.get(999L);
            assertThat(result.getChannelId()).isEqualTo(500L);
            assertThat(result.getThreadId()).isEqualTo(999L);
        }

        @Test
        @DisplayName("old format channel with null channelId: targetId = entry key")
        void oldFormatNullChannelId() {
            // Old format: channelId is null (not yet set by migration code)
            Channel ch = new Channel(100L, null, null, true, false, false, false,
                    false, SeismicIntensity.ONE, null, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(777L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            // targetId = entry key (777) since channelId and threadId are null
            assertThat(destination.exists(777L)).isTrue();
            Channel result = destination.get(777L);
            // effectiveChannelId = targetId (777) since channelId was null
            assertThat(result.getChannelId()).isEqualTo(777L);
        }

        @Test
        @DisplayName("old format channel with threadId but null channelId: targetId = threadId")
        void oldFormatWithThreadIdNullChannelId() {
            // Old format: channelId null, threadId from webhook
            Channel ch = new Channel(100L, null, 888L, true, false, false, false,
                    false, SeismicIntensity.ONE, null, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(500L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            // targetId = threadId (888)
            assertThat(destination.exists(888L)).isTrue();
            Channel result = destination.get(888L);
            // effectiveChannelId should fallback to targetId since channelId was null
            assertThat(result.getChannelId()).isEqualTo(888L);
            assertThat(result.getThreadId()).isEqualTo(888L);
        }
    }

    @Nested
    @DisplayName("migrateChannelsSql() webhook URL transformation")
    class WebhookTransformTests {

        @Test
        @DisplayName("webhook with threadId should have thread_id in URL")
        void webhookWithThreadId() {
            ChannelWebhook originalWh = ChannelWebhook.of(555L, "tok");
            Channel ch = new Channel(100L, 500L, 999L, true, false, false, false,
                    false, SeismicIntensity.ONE, originalWh, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(500L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            Channel result = destination.get(999L);
            assertThat(result.getWebhook()).isNotNull();
            assertThat(result.getWebhook().url()).contains("thread_id=999");
            assertThat(result.getWebhook().id()).isEqualTo(555L);
            assertThat(result.getWebhook().token()).isEqualTo("tok");
        }

        @Test
        @DisplayName("webhook without threadId should not have thread_id in URL")
        void webhookWithoutThreadId() {
            ChannelWebhook originalWh = ChannelWebhook.of(555L, "tok");
            Channel ch = new Channel(100L, 500L, null, true, false, false, false,
                    false, SeismicIntensity.ONE, originalWh, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(500L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            Channel result = destination.get(500L);
            assertThat(result.getWebhook()).isNotNull();
            assertThat(result.getWebhook().url()).doesNotContain("thread_id");
        }

        @Test
        @DisplayName("channel without webhook should migrate with null webhook")
        void noWebhook() {
            Channel ch = new Channel(100L, 500L, null, true, false, false, false,
                    false, SeismicIntensity.ONE, null, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(500L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            Channel result = destination.get(500L);
            assertThat(result.getWebhook()).isNull();
        }
    }

    @Nested
    @DisplayName("migrateChannelsSql() data preservation")
    class DataPreservationTests {

        @Test
        @DisplayName("should preserve all boolean flags and settings")
        void preserveAllFields() {
            Channel ch = new Channel(100L, 1L, null, true, true, true, true,
                    false, SeismicIntensity.SIX_PLUS, null, "en_us");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(1L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            Channel result = destination.get(1L);
            assertThat(result.isEewAlert()).isTrue();
            assertThat(result.isEewPrediction()).isTrue();
            assertThat(result.isEewDecimation()).isTrue();
            assertThat(result.isQuakeInfo()).isTrue();
            assertThat(result.getMinIntensity()).isEqualTo(SeismicIntensity.SIX_PLUS);
            assertThat(result.getLang()).isEqualTo("en_us");
            assertThat(result.getGuildId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should handle null lang by using database default")
        void nullLangUsesDefault() {
            Channel ch = new Channel(100L, 1L, null, false, false, false, false,
                    false, SeismicIntensity.ONE, null, null);
            List<Map.Entry<Long, Channel>> entries = List.of(entry(1L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            Channel result = destination.get(1L);
            assertThat(result.getLang()).isEqualTo("ja_jp");
        }

        @Test
        @DisplayName("should handle null minIntensity by using ONE")
        void nullMinIntensityUsesDefault() {
            Channel ch = new Channel(100L, 1L, null, false, false, false, false,
                    false, null, null, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(1L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            Channel result = destination.get(1L);
            assertThat(result.getMinIntensity()).isEqualTo(SeismicIntensity.ONE);
        }

        @Test
        @DisplayName("should migrate multiple channels in batch")
        void migrateMultipleChannels() {
            List<Map.Entry<Long, Channel>> entries = List.of(
                    entry(1L, Channel.createDefault(100L, 1L, null, "ja_jp")),
                    entry(2L, Channel.createDefault(100L, 2L, null, "en_us")),
                    entry(3L, Channel.createDefault(null, 3L, null, "zh_tw"))
            );

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            assertThat(destination.getAllChannels()).hasSize(3);
            assertThat(destination.get(1L).getLang()).isEqualTo("ja_jp");
            assertThat(destination.get(2L).getLang()).isEqualTo("en_us");
            assertThat(destination.get(3L).getLang()).isEqualTo("zh_tw");
        }

        @Test
        @DisplayName("should preserve DM channel (null guildId)")
        void preserveDmChannel() {
            Channel ch = new Channel(null, 1L, null, true, false, false, false,
                    false, SeismicIntensity.ONE, null, "ja_jp");
            List<Map.Entry<Long, Channel>> entries = List.of(entry(1L, ch));

            ChannelMigration.migrateChannelsSql(entries, destination, destination.getDsl());

            Channel result = destination.get(1L);
            assertThat(result.getGuildId()).isNull();
            assertThat(result.isGuild()).isFalse();
        }
    }

    @Nested
    @DisplayName("collectChannels()")
    class CollectChannelsTests {

        @Test
        @DisplayName("should collect channels from SQL registry")
        void collectFromSql() {
            destination.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
            destination.put(2L, Channel.createDefault(100L, 2L, null, "ja_jp"));

            List<Map.Entry<Long, Channel>> collected = ChannelMigration.collectChannels(destination);

            assertThat(collected).hasSize(2);
        }
    }

    private static Map.Entry<Long, Channel> entry(long key, Channel value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }
}
