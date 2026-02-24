package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelRegistrySqlEdgeCaseTest {

    @TempDir
    Path tempDir;

    private ChannelRegistrySql sqlRegistry;

    @BeforeEach
    void setUp() {
        Path dbPath = this.tempDir.resolve("test.db");
        this.sqlRegistry = ChannelRegistrySql.forSQLite(dbPath);
        DatabaseInitializer.migrate(this.sqlRegistry.getDataSource(), SQLDialect.SQLITE);
    }

    @AfterEach
    void tearDown() {
        if (this.sqlRegistry != null) {
            this.sqlRegistry.close();
        }
    }

    @Nested
    @DisplayName("setLang() validation")
    class SetLangValidation {

        @Test
        @DisplayName("setLang(null) should throw IllegalArgumentException")
        void setLangNullThrows() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            assertThatThrownBy(() -> sqlRegistry.setLang(1L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lang cannot be null");
        }

        @Test
        @DisplayName("setLangWithDsl(null) should throw IllegalArgumentException")
        void setLangWithDslNullThrows() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            assertThatThrownBy(() -> sqlRegistry.setLangWithDsl(sqlRegistry.getDsl(), 1L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lang cannot be null");
        }
    }

    @Nested
    @DisplayName("set() validation - unknown column names")
    class SetValidation {

        /**
         * Note: Channel.set(unknownName) silently ignores unknown fields (ifPresent pattern),
         * but ChannelRegistrySql.setWithDsl(unknownName) throws IllegalArgumentException.
         * This asymmetry is by design: the SQL layer validates strictly against SETTABLE_BOOLEAN_COLUMNS.
         */
        @Test
        @DisplayName("set() with unknown column name should throw IllegalArgumentException")
        void setUnknownColumnThrows() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            assertThatThrownBy(() -> sqlRegistry.set(1L, "nonExistentField", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown or non-settable column");
        }

        @Test
        @DisplayName("set() with valid column names should not throw")
        void setValidColumnsDoesNotThrow() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            sqlRegistry.set(1L, "eewAlert", true);
            sqlRegistry.set(1L, "eewPrediction", true);
            sqlRegistry.set(1L, "eewDecimation", true);
            sqlRegistry.set(1L, "quakeInfo", true);

            Channel ch = sqlRegistry.get(1L);
            assertThat(ch.isEewAlert()).isTrue();
            assertThat(ch.isEewPrediction()).isTrue();
            assertThat(ch.isEewDecimation()).isTrue();
            assertThat(ch.isQuakeInfo()).isTrue();
        }
    }

    @Nested
    @DisplayName("setAll() validation - unknown column names")
    class SetAllValidation {

        @Test
        @DisplayName("setAll() with unknown column name should throw IllegalArgumentException")
        void setAllUnknownColumnThrows() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            assertThatThrownBy(() -> sqlRegistry.setAll(1L, Map.of("unknownField", true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown or non-settable column");
        }

        @Test
        @DisplayName("setAll() with mixed valid and unknown columns should throw on unknown")
        void setAllMixedThrows() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            assertThatThrownBy(() -> sqlRegistry.setAll(1L, Map.of("eewAlert", true, "badField", false)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("setAll() with empty map should not throw")
        void setAllEmptyIsNoop() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));
            sqlRegistry.setAll(1L, Map.of()); // should not throw
        }
    }

    @Nested
    @DisplayName("clearWebhookByUrls() webhook ID matching")
    class ClearWebhookByUrlsTests {

        @Test
        @DisplayName("should match by webhook ID, ignoring thread_id in URL")
        void matchByWebhookIdIgnoresThreadId() {
            // Insert channel with webhook URL that includes thread_id
            ChannelWebhook webhookWithThread = ChannelWebhook.of(555L, "tok", 999L);
            Channel ch = new Channel(100L, 1L, 999L, true, false, false, false,
                    SeismicIntensity.ONE, webhookWithThread, "ja_jp");
            sqlRegistry.put(999L, ch);

            // Clear using URL WITHOUT thread_id - should still match because it extracts webhook ID
            int cleared = sqlRegistry.clearWebhookByUrls(
                    List.of("https://discord.com/api/webhooks/555/tok"));

            assertThat(cleared).isEqualTo(1);
            assertThat(sqlRegistry.get(999L).getWebhook()).isNull();
        }

        @Test
        @DisplayName("should match by webhook ID even with different token in clear URL")
        void matchByWebhookIdRegardlessOfToken() {
            // The clear URL has same webhook ID but different token
            ChannelWebhook webhook = ChannelWebhook.of(555L, "originalToken");
            Channel ch = new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, webhook, "ja_jp");
            sqlRegistry.put(1L, ch);

            // Clear using URL with same ID but different token
            int cleared = sqlRegistry.clearWebhookByUrls(
                    List.of("https://discord.com/api/webhooks/555/differentToken"));

            // Should match because WEBHOOK_ID.in(webhookIds) matches by ID only
            assertThat(cleared).isEqualTo(1);
        }

        @Test
        @DisplayName("should clear multiple channels sharing same webhook ID")
        void clearMultipleChannelsSameWebhookId() {
            // Two channels (channel + thread) sharing same webhook ID
            ChannelWebhook wh1 = ChannelWebhook.of(555L, "tok");
            ChannelWebhook wh2 = ChannelWebhook.of(555L, "tok", 999L);

            sqlRegistry.put(1L, new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, wh1, "ja_jp"));
            sqlRegistry.put(999L, new Channel(100L, 1L, 999L, true, false, false, false,
                    SeismicIntensity.ONE, wh2, "ja_jp"));

            int cleared = sqlRegistry.clearWebhookByUrls(
                    List.of("https://discord.com/api/webhooks/555/tok"));

            assertThat(cleared).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("loadAllForSnapshot() default value conversion")
    class LoadAllForSnapshotTests {

        @Test
        @DisplayName("should convert null min_intensity to ONE")
        void nullMinIntensityDefaultsToOne() {
            // Insert with explicit values
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            List<DeliverySnapshot.DeliveryChannel> channels = sqlRegistry.loadAllForSnapshot();
            assertThat(channels).hasSize(1);
            assertThat(channels.get(0).minIntensity()).isEqualTo(SeismicIntensity.ONE);
        }

        @Test
        @DisplayName("should handle SQLite Integer->Long conversion for IDs")
        void sqliteIntegerToLongConversion() {
            // Small IDs that SQLite might return as Integer instead of Long
            sqlRegistry.put(1L, Channel.createDefault(2L, 3L, null, "ja_jp"));

            List<DeliverySnapshot.DeliveryChannel> channels = sqlRegistry.loadAllForSnapshot();
            assertThat(channels).hasSize(1);
            assertThat(channels.get(0).targetId()).isEqualTo(1L);
            assertThat(channels.get(0).channelId()).isEqualTo(3L);
            assertThat(channels.get(0).guildId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should handle large snowflake IDs correctly")
        void largeSnowflakeIds() {
            long largeId = 1234567890123456789L;
            sqlRegistry.put(largeId, Channel.createDefault(largeId, largeId, null, "ja_jp"));

            List<DeliverySnapshot.DeliveryChannel> channels = sqlRegistry.loadAllForSnapshot();
            assertThat(channels).hasSize(1);
            assertThat(channels.get(0).targetId()).isEqualTo(largeId);
        }

        @Test
        @DisplayName("should map boolean fields correctly (0/1 to false/true)")
        void booleanFieldMapping() {
            Channel ch = new Channel(100L, 1L, null, true, false, true, false,
                    SeismicIntensity.FOUR, null, "en_us");
            sqlRegistry.put(1L, ch);

            List<DeliverySnapshot.DeliveryChannel> channels = sqlRegistry.loadAllForSnapshot();
            assertThat(channels).hasSize(1);
            DeliverySnapshot.DeliveryChannel dc = channels.get(0);
            assertThat(dc.eewAlert()).isTrue();
            assertThat(dc.eewPrediction()).isFalse();
            assertThat(dc.eewDecimation()).isTrue();
            assertThat(dc.quakeInfo()).isFalse();
            assertThat(dc.minIntensity()).isEqualTo(SeismicIntensity.FOUR);
            assertThat(dc.lang()).isEqualTo("en_us");
        }

        @Test
        @DisplayName("should include webhook in DeliveryChannel when present")
        void webhookIncluded() {
            ChannelWebhook webhook = ChannelWebhook.of(555L, "myToken");
            Channel ch = new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, webhook, "ja_jp");
            sqlRegistry.put(1L, ch);

            List<DeliverySnapshot.DeliveryChannel> channels = sqlRegistry.loadAllForSnapshot();
            assertThat(channels).hasSize(1);
            assertThat(channels.get(0).webhook()).isNotNull();
            assertThat(channels.get(0).webhook().id()).isEqualTo(555L);
        }

        @Test
        @DisplayName("should set null webhook in DeliveryChannel when absent")
        void webhookAbsent() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            List<DeliverySnapshot.DeliveryChannel> channels = sqlRegistry.loadAllForSnapshot();
            assertThat(channels).hasSize(1);
            assertThat(channels.get(0).webhook()).isNull();
        }
    }

    @Nested
    @DisplayName("clearWebhookByUrls() invalid URL handling")
    class ClearWebhookInvalidUrlTests {

        @Test
        @DisplayName("invalid URL returns 0 and does not crash")
        void invalidUrlReturnsZeroNoCrash() {
            // Insert a channel with valid webhook so DB isn't empty
            ChannelWebhook webhook = ChannelWebhook.of(555L, "tok");
            sqlRegistry.put(1L, new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, webhook, "ja_jp"));

            int cleared = sqlRegistry.clearWebhookByUrls(List.of("not-a-url"));

            assertThat(cleared).isZero();
            // Webhook should remain untouched
            assertThat(sqlRegistry.get(1L).getWebhook()).isNotNull();
        }

        @Test
        @DisplayName("mix of valid and invalid URLs clears only valid matches")
        void mixValidInvalidUrls() {
            ChannelWebhook webhook = ChannelWebhook.of(555L, "tok");
            sqlRegistry.put(1L, new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, webhook, "ja_jp"));

            int cleared = sqlRegistry.clearWebhookByUrls(
                    List.of("not-a-url", "https://discord.com/api/webhooks/555/tok"));

            assertThat(cleared).isEqualTo(1);
            assertThat(sqlRegistry.get(1L).getWebhook()).isNull();
        }

        @Test
        @DisplayName("empty string URL is skipped, returns 0")
        void emptyStringUrlSkipped() {
            ChannelWebhook webhook = ChannelWebhook.of(555L, "tok");
            sqlRegistry.put(1L, new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, webhook, "ja_jp"));

            int cleared = sqlRegistry.clearWebhookByUrls(List.of(""));

            assertThat(cleared).isZero();
            assertThat(sqlRegistry.get(1L).getWebhook()).isNotNull();
        }
    }

    @Nested
    @DisplayName("put() with onConflictDoNothing")
    class PutConflictTests {

        @Test
        @DisplayName("duplicate put should not overwrite existing channel")
        void duplicatePutDoesNotOverwrite() {
            Channel original = new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, null, "ja_jp");
            Channel duplicate = new Channel(200L, 2L, null, false, true, true, true,
                    SeismicIntensity.SEVEN, null, "en_us");

            sqlRegistry.put(1L, original);
            sqlRegistry.put(1L, duplicate); // onConflictDoNothing

            Channel result = sqlRegistry.get(1L);
            assertThat(result.getGuildId()).isEqualTo(100L); // original value
            assertThat(result.isEewAlert()).isTrue(); // original value
        }
    }

    @Nested
    @DisplayName("isWebhookForThread()")
    class IsWebhookForThreadTests {

        @Test
        @DisplayName("should return true when webhook is only used by the given target")
        void singleUse() {
            ChannelWebhook webhook = ChannelWebhook.of(555L, "tok");
            sqlRegistry.put(1L, new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, webhook, "ja_jp"));

            assertThat(sqlRegistry.isWebhookForThread(555L, 1L)).isTrue();
        }

        @Test
        @DisplayName("should return false when webhook is used by another target")
        void multipleUse() {
            ChannelWebhook wh1 = ChannelWebhook.of(555L, "tok");
            ChannelWebhook wh2 = ChannelWebhook.of(555L, "tok", 999L);

            sqlRegistry.put(1L, new Channel(100L, 1L, null, true, false, false, false,
                    SeismicIntensity.ONE, wh1, "ja_jp"));
            sqlRegistry.put(999L, new Channel(100L, 1L, 999L, true, false, false, false,
                    SeismicIntensity.ONE, wh2, "ja_jp"));

            // webhookId 555 is used by both target 1 and 999
            assertThat(sqlRegistry.isWebhookForThread(555L, 1L)).isFalse();
            assertThat(sqlRegistry.isWebhookForThread(555L, 999L)).isFalse();
        }

        @Test
        @DisplayName("should return true for non-existent webhook ID")
        void nonExistentWebhook() {
            sqlRegistry.put(1L, Channel.createDefault(100L, 1L, null, "ja_jp"));

            // No conflict found → true
            assertThat(sqlRegistry.isWebhookForThread(999L, 1L)).isTrue();
        }
    }
}
