package net.teamfruit.eewbot.registry.destination.model;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.*;

class ChannelFilterTest {

    private Channel channel(Long guildId, Long channelId, Long threadId,
                            boolean eewAlert, boolean eewPrediction, boolean eewDecimation, boolean quakeInfo,
                            SeismicIntensity minIntensity, ChannelWebhook webhook) {
        return new Channel(guildId, channelId, threadId, eewAlert, eewPrediction, eewDecimation, quakeInfo, false, minIntensity, webhook, "ja_jp");
    }

    @Nested
    @DisplayName("test(Channel)")
    class TestMethod {

        @Test
        @DisplayName("empty filter should match any channel")
        void emptyFilterMatchesAll() {
            ChannelFilter filter = ChannelFilter.builder().build();
            Channel ch = channel(100L, 1L, null, true, false, false, true, SeismicIntensity.ONE, null);
            assertThat(filter.test(ch)).isTrue();
        }

        @Test
        @DisplayName("hasGuild=true should match guild channel")
        void hasGuildTrue() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(true).build();
            assertThat(filter.test(channel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("hasGuild=false should match DM channel")
        void hasGuildFalse() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(false).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("guildId filter should match exact guild")
        void guildIdExact() {
            ChannelFilter filter = ChannelFilter.builder().guildId(100L).build();
            assertThat(filter.test(channel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(200L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("channelId filter should match exact channel")
        void channelIdExact() {
            ChannelFilter filter = ChannelFilter.builder().channelId(50L).build();
            assertThat(filter.test(channel(null, 50L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 99L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("threadId=null filter should match non-thread channels")
        void threadIdNull() {
            ChannelFilter filter = ChannelFilter.builder().threadId(null).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, 999L, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("threadId=value filter should match specific thread")
        void threadIdValue() {
            ChannelFilter filter = ChannelFilter.builder().threadId(999L).build();
            assertThat(filter.test(channel(null, 1L, 999L, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("isThread=true should match channels with threadId")
        void isThreadTrue() {
            ChannelFilter filter = ChannelFilter.builder().isThread(true).build();
            assertThat(filter.test(channel(null, 1L, 123L, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("isThread=false should match channels without threadId")
        void isThreadFalse() {
            ChannelFilter filter = ChannelFilter.builder().isThread(false).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, 123L, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("eewAlert filter")
        void eewAlertFilter() {
            ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();
            assertThat(filter.test(channel(null, 1L, null, true, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("eewPrediction filter")
        void eewPredictionFilter() {
            ChannelFilter filter = ChannelFilter.builder().eewPrediction(true).build();
            assertThat(filter.test(channel(null, 1L, null, false, true, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("eewDecimation filter")
        void eewDecimationFilter() {
            ChannelFilter filter = ChannelFilter.builder().eewDecimation(true).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, true, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("quakeInfo filter")
        void quakeInfoFilter() {
            ChannelFilter filter = ChannelFilter.builder().quakeInfo(true).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, true, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("intensity filter should match channels with minIntensity <= threshold")
        void intensityFilter() {
            ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.FOUR).build();
            // minIntensity <= 4: pass
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.FOUR, null))).isTrue();
            // minIntensity > 4: fail
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.FIVE_MINUS, null))).isFalse();
        }

        @Test
        @DisplayName("webhookId filter should match channel with matching webhook ID")
        void webhookIdFilter() {
            ChannelWebhook wh = ChannelWebhook.of(555L, "token");
            ChannelFilter filter = ChannelFilter.builder().webhookId(555L).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, wh))).isTrue();
        }

        @Test
        @DisplayName("webhookId filter should reject channel without webhook")
        void webhookIdFilterNoWebhook() {
            ChannelFilter filter = ChannelFilter.builder().webhookId(555L).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("webhookId filter should reject channel with different webhook ID")
        void webhookIdFilterMismatch() {
            ChannelWebhook wh = ChannelWebhook.of(666L, "token");
            ChannelFilter filter = ChannelFilter.builder().webhookId(555L).build();
            assertThat(filter.test(channel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, wh))).isFalse();
        }

        @Test
        @DisplayName("multiple filters should be ANDed together")
        void multipleFilters() {
            ChannelFilter filter = ChannelFilter.builder()
                    .hasGuild(true)
                    .eewAlert(true)
                    .build();
            // Both match
            assertThat(filter.test(channel(100L, 1L, null, true, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            // Guild match, eewAlert mismatch
            assertThat(filter.test(channel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
            // Guild mismatch
            assertThat(filter.test(channel(null, 1L, null, true, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }
    }

    private DeliverySnapshot.DeliveryChannel deliveryChannel(Long guildId, long channelId, Long threadId,
                                                             boolean eewAlert, boolean eewPrediction, boolean eewDecimation, boolean quakeInfo,
                                                             SeismicIntensity minIntensity, ChannelWebhook webhook) {
        return new DeliverySnapshot.DeliveryChannel(0L, channelId, threadId, guildId,
                eewAlert, eewPrediction, eewDecimation, quakeInfo, false, minIntensity, "ja_jp", webhook);
    }

    @Nested
    @DisplayName("test(DeliveryChannel)")
    class TestDeliveryChannelMethod {

        @Test
        @DisplayName("empty filter should match any delivery channel")
        void emptyFilterMatchesAll() {
            ChannelFilter filter = ChannelFilter.builder().build();
            DeliverySnapshot.DeliveryChannel ch = deliveryChannel(100L, 1L, null, true, false, false, true, SeismicIntensity.ONE, null);
            assertThat(filter.test(ch)).isTrue();
        }

        @Test
        @DisplayName("hasGuild=true should match guild delivery channel")
        void hasGuildTrue() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(true).build();
            assertThat(filter.test(deliveryChannel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("hasGuild=false should match DM delivery channel")
        void hasGuildFalse() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(false).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("guildId filter should match exact guild")
        void guildIdExact() {
            ChannelFilter filter = ChannelFilter.builder().guildId(100L).build();
            assertThat(filter.test(deliveryChannel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(200L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("channelId filter should match exact channel")
        void channelIdExact() {
            ChannelFilter filter = ChannelFilter.builder().channelId(50L).build();
            assertThat(filter.test(deliveryChannel(null, 50L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 99L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("threadId=null filter should match non-thread channels")
        void threadIdNull() {
            ChannelFilter filter = ChannelFilter.builder().threadId(null).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, 999L, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("threadId=value filter should match specific thread")
        void threadIdValue() {
            ChannelFilter filter = ChannelFilter.builder().threadId(999L).build();
            assertThat(filter.test(deliveryChannel(null, 1L, 999L, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("isThread=true should match channels with threadId")
        void isThreadTrue() {
            ChannelFilter filter = ChannelFilter.builder().isThread(true).build();
            assertThat(filter.test(deliveryChannel(null, 1L, 123L, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("isThread=false should match channels without threadId")
        void isThreadFalse() {
            ChannelFilter filter = ChannelFilter.builder().isThread(false).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, 123L, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("eewAlert filter")
        void eewAlertFilter() {
            ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, true, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("eewPrediction filter")
        void eewPredictionFilter() {
            ChannelFilter filter = ChannelFilter.builder().eewPrediction(true).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, true, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("eewDecimation filter")
        void eewDecimationFilter() {
            ChannelFilter filter = ChannelFilter.builder().eewDecimation(true).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, true, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("quakeInfo filter")
        void quakeInfoFilter() {
            ChannelFilter filter = ChannelFilter.builder().quakeInfo(true).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, true, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("intensity filter should match channels with minIntensity <= threshold")
        void intensityFilter() {
            ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.FOUR).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.FOUR, null))).isTrue();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.FIVE_MINUS, null))).isFalse();
        }

        @Test
        @DisplayName("intensity filter should reject null minIntensity")
        void intensityFilterNullIntensity() {
            ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.FOUR).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, null, null))).isFalse();
        }

        @Test
        @DisplayName("webhookId filter should match channel with matching webhook ID")
        void webhookIdFilter() {
            ChannelWebhook wh = ChannelWebhook.of(555L, "token");
            ChannelFilter filter = ChannelFilter.builder().webhookId(555L).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, wh))).isTrue();
        }

        @Test
        @DisplayName("webhookId filter should reject channel without webhook")
        void webhookIdFilterNoWebhook() {
            ChannelFilter filter = ChannelFilter.builder().webhookId(555L).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }

        @Test
        @DisplayName("webhookId filter should reject channel with different webhook ID")
        void webhookIdFilterMismatch() {
            ChannelWebhook wh = ChannelWebhook.of(666L, "token");
            ChannelFilter filter = ChannelFilter.builder().webhookId(555L).build();
            assertThat(filter.test(deliveryChannel(null, 1L, null, false, false, false, false, SeismicIntensity.ONE, wh))).isFalse();
        }

        @Test
        @DisplayName("multiple filters should be ANDed together")
        void multipleFilters() {
            ChannelFilter filter = ChannelFilter.builder()
                    .hasGuild(true)
                    .eewAlert(true)
                    .build();
            assertThat(filter.test(deliveryChannel(100L, 1L, null, true, false, false, false, SeismicIntensity.ONE, null))).isTrue();
            assertThat(filter.test(deliveryChannel(100L, 1L, null, false, false, false, false, SeismicIntensity.ONE, null))).isFalse();
            assertThat(filter.test(deliveryChannel(null, 1L, null, true, false, false, false, SeismicIntensity.ONE, null))).isFalse();
        }
    }

    @Nested
    @DisplayName("toCondition() - SQL equivalence with test()")
    class ToConditionTests {

        @TempDir
        Path tempDir;

        private DSLContext dsl;
        private ChannelRegistrySql sqlRegistry;

        /**
         * Test channels covering guild/DM, thread/non-thread, webhook/no-webhook,
         * various boolean flag combinations, and different intensity levels.
         */
        private final Map<Long, Channel> testChannels = Map.of(
                // Guild channel, eewAlert=true, quakeInfo=true, intensity=ONE, no webhook, no thread
                1001L, new Channel(100L, 1001L, null, true, false, false, true, false, SeismicIntensity.ONE, null, "ja_jp"),
                // DM channel, eewPrediction=true, intensity=FOUR, no webhook, no thread
                2001L, new Channel(null, 2001L, null, false, true, false, false, false, SeismicIntensity.FOUR, null, "en_us"),
                // Guild channel with thread, eewDecimation=true, intensity=FIVE_MINUS
                3001L, new Channel(200L, 3000L, 3001L, false, false, true, false, false, SeismicIntensity.FIVE_MINUS, null, "ja_jp"),
                // Guild channel with webhook, eewAlert=true, eewPrediction=true, intensity=TWO
                4001L, new Channel(100L, 4001L, null, true, true, false, false, false, SeismicIntensity.TWO,
                        ChannelWebhook.of(555L, "token"), "ja_jp"),
                // DM channel, all flags false, intensity=THREE
                5001L, new Channel(null, 5001L, null, false, false, false, false, false, SeismicIntensity.THREE, null, "ja_jp"),
                // Guild channel with thread and webhook, quakeInfo=true, intensity=ONE
                6001L, new Channel(300L, 6000L, 6001L, false, false, false, true, false, SeismicIntensity.ONE,
                        ChannelWebhook.of(666L, "token2"), "ja_jp")
        );

        @BeforeEach
        void setUp() throws IOException {
            Path dbPath = this.tempDir.resolve("filter_test.db");
            this.sqlRegistry = ChannelRegistrySql.forSQLite(dbPath);
            DatabaseInitializer.migrate(this.sqlRegistry.getDataSource(), SQLDialect.SQLITE);
            this.dsl = this.sqlRegistry.getDsl();

            // Insert all test channels
            for (Map.Entry<Long, Channel> entry : this.testChannels.entrySet()) {
                this.sqlRegistry.put(entry.getKey(), entry.getValue());
            }
        }

        private Set<Long> queryTargetIds(ChannelFilter filter) {
            return this.dsl.select(field(name("target_id"), Long.class))
                    .from(table(name("destinations")))
                    .where(filter.toCondition())
                    .fetchSet(field(name("target_id"), Long.class));
        }

        private Set<Long> filterTargetIds(ChannelFilter filter) {
            return this.testChannels.entrySet().stream()
                    .filter(e -> filter.test(e.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }

        private void assertConditionMatchesTest(ChannelFilter filter) {
            Set<Long> sqlResult = queryTargetIds(filter);
            Set<Long> javaResult = filterTargetIds(filter);
            assertThat(sqlResult).as("toCondition() SQL result should match test() result for filter: %s", filter)
                    .isEqualTo(javaResult);
        }

        @Test
        @DisplayName("empty filter should match all rows")
        void emptyFilter() {
            ChannelFilter filter = ChannelFilter.builder().build();
            assertConditionMatchesTest(filter);
            // Additionally verify it returns all channels
            assertThat(queryTargetIds(filter)).hasSize(this.testChannels.size());
        }

        @Test
        @DisplayName("hasGuild=true should match guild channels only")
        void hasGuildTrue() {
            assertConditionMatchesTest(ChannelFilter.builder().hasGuild(true).build());
        }

        @Test
        @DisplayName("hasGuild=false should match DM channels only")
        void hasGuildFalse() {
            assertConditionMatchesTest(ChannelFilter.builder().hasGuild(false).build());
        }

        @Test
        @DisplayName("eewAlert=true should match alert-enabled channels")
        void eewAlertTrue() {
            assertConditionMatchesTest(ChannelFilter.builder().eewAlert(true).build());
        }

        @Test
        @DisplayName("eewAlert=false should match alert-disabled channels")
        void eewAlertFalse() {
            assertConditionMatchesTest(ChannelFilter.builder().eewAlert(false).build());
        }

        @Test
        @DisplayName("intensity filter should match channels with min_intensity <= threshold")
        void intensityFilter() {
            assertConditionMatchesTest(ChannelFilter.builder().intensity(SeismicIntensity.FOUR).build());
        }

        @Test
        @DisplayName("threadId=null should match non-thread channels")
        void threadIdNull() {
            assertConditionMatchesTest(ChannelFilter.builder().threadId(null).build());
        }

        @Test
        @DisplayName("threadId=value should match specific thread")
        void threadIdValue() {
            assertConditionMatchesTest(ChannelFilter.builder().threadId(3001L).build());
        }

        @Test
        @DisplayName("combined filter should match intersection")
        void combinedFilter() {
            assertConditionMatchesTest(ChannelFilter.builder()
                    .hasGuild(true)
                    .eewAlert(true)
                    .build());
        }

        @Test
        @DisplayName("guildId filter should match exact guild")
        void guildIdFilter() {
            assertConditionMatchesTest(ChannelFilter.builder().guildId(100L).build());
        }

        @Test
        @DisplayName("channelId filter should match exact channel")
        void channelIdFilter() {
            assertConditionMatchesTest(ChannelFilter.builder().channelId(1001L).build());
        }

        @Test
        @DisplayName("isThread=true should match channels with threadId")
        void isThreadTrue() {
            assertConditionMatchesTest(ChannelFilter.builder().isThread(true).build());
        }

        @Test
        @DisplayName("isThread=false should match channels without threadId")
        void isThreadFalse() {
            assertConditionMatchesTest(ChannelFilter.builder().isThread(false).build());
        }

        @Test
        @DisplayName("eewPrediction=true filter")
        void eewPredictionTrue() {
            assertConditionMatchesTest(ChannelFilter.builder().eewPrediction(true).build());
        }

        @Test
        @DisplayName("eewPrediction=false filter")
        void eewPredictionFalse() {
            assertConditionMatchesTest(ChannelFilter.builder().eewPrediction(false).build());
        }

        @Test
        @DisplayName("eewDecimation=true filter")
        void eewDecimationTrue() {
            assertConditionMatchesTest(ChannelFilter.builder().eewDecimation(true).build());
        }

        @Test
        @DisplayName("eewDecimation=false filter")
        void eewDecimationFalse() {
            assertConditionMatchesTest(ChannelFilter.builder().eewDecimation(false).build());
        }

        @Test
        @DisplayName("quakeInfo=true filter")
        void quakeInfoTrue() {
            assertConditionMatchesTest(ChannelFilter.builder().quakeInfo(true).build());
        }

        @Test
        @DisplayName("quakeInfo=false filter")
        void quakeInfoFalse() {
            assertConditionMatchesTest(ChannelFilter.builder().quakeInfo(false).build());
        }

        @Test
        @DisplayName("webhookId filter should match channel with matching webhook")
        void webhookIdFilter() {
            assertConditionMatchesTest(ChannelFilter.builder().webhookId(555L).build());
        }

        @Test
        @DisplayName("webhookId filter with non-existent ID returns empty")
        void webhookIdNonExistent() {
            ChannelFilter filter = ChannelFilter.builder().webhookId(999999L).build();
            assertConditionMatchesTest(filter);
            assertThat(queryTargetIds(filter)).isEmpty();
        }

        @Test
        @DisplayName("combined guildId + eewPrediction + intensity filter")
        void combinedGuildPredictionIntensity() {
            assertConditionMatchesTest(ChannelFilter.builder()
                    .guildId(100L)
                    .eewPrediction(true)
                    .intensity(SeismicIntensity.THREE)
                    .build());
        }

        @Test
        @DisplayName("combined isThread + quakeInfo filter")
        void combinedIsThreadQuakeInfo() {
            assertConditionMatchesTest(ChannelFilter.builder()
                    .isThread(true)
                    .quakeInfo(true)
                    .build());
        }
    }

    @Nested
    @DisplayName("toQueryString()")
    class ToQueryStringTests {

        @Test
        @DisplayName("empty filter should produce empty query string")
        void emptyFilter() {
            ChannelFilter filter = ChannelFilter.builder().build();
            assertThat(filter.toQueryString()).isEmpty();
        }

        @Test
        @DisplayName("hasGuild=true should produce @guildId range query")
        void hasGuildTrue() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(true).build();
            assertThat(filter.toQueryString()).contains("@guildId:[0 inf]");
        }

        @Test
        @DisplayName("hasGuild=false should produce negated @guildId range query")
        void hasGuildFalse() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(false).build();
            assertThat(filter.toQueryString()).contains("-@guildId:[0 inf]");
        }

        @Test
        @DisplayName("guildId should produce exact range query")
        void guildId() {
            ChannelFilter filter = ChannelFilter.builder().guildId(42L).build();
            assertThat(filter.toQueryString()).contains("@guildId:[42 42]");
        }

        @Test
        @DisplayName("channelId should produce exact range query")
        void channelId() {
            ChannelFilter filter = ChannelFilter.builder().channelId(99L).build();
            assertThat(filter.toQueryString()).contains("@channelId:[99 99]");
        }

        @Test
        @DisplayName("eewAlert=true should produce tag query")
        void eewAlert() {
            ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();
            assertThat(filter.toQueryString()).contains("@eewAlert:{true}");
        }

        @Test
        @DisplayName("intensity should produce range query with code")
        void intensity() {
            ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.FOUR).build();
            assertThat(filter.toQueryString()).contains("@minIntensity:[0 4]");
        }

        @Test
        @DisplayName("webhookId should produce exact range query")
        void webhookId() {
            ChannelFilter filter = ChannelFilter.builder().webhookId(123L).build();
            assertThat(filter.toQueryString()).contains("@webhookId:[123 123]");
        }

        @Test
        @DisplayName("threadId=null should produce negated range query")
        void threadIdNull() {
            ChannelFilter filter = ChannelFilter.builder().threadId(null).build();
            assertThat(filter.toQueryString()).contains("-@threadId:[0 inf]");
        }

        @Test
        @DisplayName("isThread=true should produce @threadId range query")
        void isThreadTrue() {
            ChannelFilter filter = ChannelFilter.builder().isThread(true).build();
            assertThat(filter.toQueryString()).contains("@threadId:[0 inf]");
        }

        @Test
        @DisplayName("isThread=false should produce negated @threadId range query")
        void isThreadFalse() {
            ChannelFilter filter = ChannelFilter.builder().isThread(false).build();
            assertThat(filter.toQueryString()).contains("-@threadId:[0 inf]");
        }
    }
}
