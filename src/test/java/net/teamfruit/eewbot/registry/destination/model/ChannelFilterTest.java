package net.teamfruit.eewbot.registry.destination.model;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelFilterTest {

    private Channel channel(Long guildId, Long channelId, Long threadId,
                            boolean eewAlert, boolean eewPrediction, boolean eewDecimation, boolean quakeInfo,
                            SeismicIntensity minIntensity, ChannelWebhook webhook) {
        return new Channel(guildId, channelId, threadId, eewAlert, eewPrediction, eewDecimation, quakeInfo, minIntensity, webhook, "ja_jp");
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

    @Nested
    @DisplayName("toCondition()")
    class ToConditionTests {

        @Test
        @DisplayName("empty filter should produce noCondition")
        void emptyFilter() {
            ChannelFilter filter = ChannelFilter.builder().build();
            // noCondition renders as TRUE
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("true").doesNotContain("guild_id");
        }

        @Test
        @DisplayName("hasGuild=true should produce guild_id is not null condition")
        void hasGuildTrue() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(true).build();
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("guild_id").containsIgnoringCase("is not null");
        }

        @Test
        @DisplayName("hasGuild=false should produce guild_id is null condition")
        void hasGuildFalse() {
            ChannelFilter filter = ChannelFilter.builder().hasGuild(false).build();
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("guild_id").containsIgnoringCase("is null");
        }

        @Test
        @DisplayName("eewAlert=true should produce eew_alert = 1")
        void eewAlertCondition() {
            ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("eew_alert").contains("1");
        }

        @Test
        @DisplayName("eewAlert=false should produce eew_alert = 0")
        void eewAlertFalseCondition() {
            ChannelFilter filter = ChannelFilter.builder().eewAlert(false).build();
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("eew_alert").contains("0");
        }

        @Test
        @DisplayName("intensity filter should produce min_intensity <= code")
        void intensityCondition() {
            ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.FOUR).build();
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("min_intensity");
        }

        @Test
        @DisplayName("threadId=null should produce thread_id is null")
        void threadIdNullCondition() {
            ChannelFilter filter = ChannelFilter.builder().threadId(null).build();
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("thread_id").containsIgnoringCase("is null");
        }

        @Test
        @DisplayName("threadId=value should produce thread_id = value")
        void threadIdValueCondition() {
            ChannelFilter filter = ChannelFilter.builder().threadId(999L).build();
            String sql = filter.toCondition().toString();
            assertThat(sql).containsIgnoringCase("thread_id").contains("999");
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
