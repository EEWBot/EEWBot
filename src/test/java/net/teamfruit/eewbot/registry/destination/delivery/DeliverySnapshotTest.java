package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliverySnapshotTest {

    private static DeliverySnapshot.DeliveryChannel dc(long targetId, long channelId, Long threadId, Long guildId,
                                                       boolean eewAlert, boolean eewPrediction, boolean eewDecimation, boolean quakeInfo,
                                                       boolean tsunami, SeismicIntensity minIntensity, String lang, ChannelWebhook webhook) {
        return new DeliverySnapshot.DeliveryChannel(targetId, channelId, threadId, guildId,
                eewAlert, eewPrediction, eewDecimation, quakeInfo, tsunami, minIntensity, lang, webhook);
    }

    private static DeliverySnapshot.DeliveryChannel simple(long targetId, long channelId, Long guildId, boolean eewAlert, ChannelWebhook webhook) {
        return dc(targetId, channelId, null, guildId, eewAlert, false, false, false, false, SeismicIntensity.ONE, "ja_jp", webhook);
    }

    @Nested
    @DisplayName("Constructor and indexing")
    class IndexTests {

        @Test
        @DisplayName("empty channel list should create empty snapshot")
        void emptySnapshot() {
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of());
            assertThat(snapshot.size()).isZero();
            assertThat(snapshot.getRevision()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should index channels by targetId")
        void indexByTargetId() {
            DeliverySnapshot snapshot = new DeliverySnapshot(5L, List.of(
                    simple(10L, 10L, null, true, null),
                    simple(20L, 20L, null, false, null)
            ));
            assertThat(snapshot.size()).isEqualTo(2);
            assertThat(snapshot.get(10L)).isNotNull();
            assertThat(snapshot.get(10L).eewAlert()).isTrue();
            assertThat(snapshot.get(20L)).isNotNull();
            assertThat(snapshot.get(20L).eewAlert()).isFalse();
            assertThat(snapshot.get(999L)).isNull();
        }

        @Test
        @DisplayName("should track revision")
        void revision() {
            DeliverySnapshot snapshot = new DeliverySnapshot(42L, List.of());
            assertThat(snapshot.getRevision()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("getPartitionedByWebhook()")
    class PartitionTests {

        @Test
        @DisplayName("should partition channels by webhook presence")
        void partitionByWebhook() {
            ChannelWebhook wh = ChannelWebhook.of(111L, "tok");
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    simple(1L, 1L, null, true, wh),
                    simple(2L, 2L, null, true, null),
                    simple(3L, 3L, null, true, ChannelWebhook.of(222L, "tok2"))
            ));

            DeliveryPartition partition = snapshot.getPartitionedByWebhook(ChannelFilter.builder().build());
            assertThat(partition.webhook()).hasSize(2);
            assertThat(partition.webhook()).containsKey(1L);
            assertThat(partition.webhook()).containsKey(3L);
            assertThat(partition.direct()).hasSize(1);
            assertThat(partition.direct()).containsKey(2L);
        }

        @Test
        @DisplayName("null filter should accept all channels")
        void nullFilter() {
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    simple(1L, 1L, null, true, null),
                    simple(2L, 2L, null, false, null)
            ));

            DeliveryPartition partition = snapshot.getPartitionedByWebhook(null);
            assertThat(partition.direct()).hasSize(2);
        }

        @Test
        @DisplayName("should filter by eewAlert")
        void filterByEewAlert() {
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    simple(1L, 1L, null, true, null),
                    simple(2L, 2L, null, false, null)
            ));

            ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();
            DeliveryPartition partition = snapshot.getPartitionedByWebhook(filter);
            assertThat(partition.direct()).hasSize(1);
            assertThat(partition.direct()).containsKey(1L);
        }

        @Test
        @DisplayName("should filter by hasGuild")
        void filterByHasGuild() {
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    simple(1L, 1L, 100L, true, null),
                    simple(2L, 2L, null, true, null)
            ));

            ChannelFilter guildFilter = ChannelFilter.builder().hasGuild(true).build();
            DeliveryPartition partition = snapshot.getPartitionedByWebhook(guildFilter);
            assertThat(partition.direct()).hasSize(1);
            assertThat(partition.direct()).containsKey(1L);
        }

        @Test
        @DisplayName("should filter by intensity threshold")
        void filterByIntensity() {
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    dc(1L, 1L, null, null, true, false, false, false, false, SeismicIntensity.ONE, "ja_jp", null),
                    dc(2L, 2L, null, null, true, false, false, false, false, SeismicIntensity.FOUR, "ja_jp", null),
                    dc(3L, 3L, null, null, true, false, false, false, false, SeismicIntensity.SEVEN, "ja_jp", null)
            ));

            ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.FOUR).build();
            DeliveryPartition partition = snapshot.getPartitionedByWebhook(filter);
            // minIntensity <= 4: targets 1 (code=1) and 2 (code=4)
            assertThat(partition.direct()).hasSize(2);
            assertThat(partition.direct()).containsKey(1L);
            assertThat(partition.direct()).containsKey(2L);
        }

        @Test
        @DisplayName("should filter by webhookId")
        void filterByWebhookId() {
            ChannelWebhook wh1 = ChannelWebhook.of(555L, "tok1");
            ChannelWebhook wh2 = ChannelWebhook.of(666L, "tok2");
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    simple(1L, 1L, null, true, wh1),
                    simple(2L, 2L, null, true, wh2),
                    simple(3L, 3L, null, true, null)
            ));

            ChannelFilter filter = ChannelFilter.builder().webhookId(555L).build();
            DeliveryPartition partition = snapshot.getPartitionedByWebhook(filter);
            assertThat(partition.webhook()).hasSize(1);
            assertThat(partition.webhook()).containsKey(1L);
            assertThat(partition.direct()).isEmpty();
        }

        @Test
        @DisplayName("should filter by threadId presence (isThread)")
        void filterByIsThread() {
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    dc(1L, 1L, 999L, null, true, false, false, false, false, SeismicIntensity.ONE, "ja_jp", null),
                    dc(2L, 2L, null, null, true, false, false, false, false, SeismicIntensity.ONE, "ja_jp", null)
            ));

            ChannelFilter filter = ChannelFilter.builder().isThread(true).build();
            DeliveryPartition partition = snapshot.getPartitionedByWebhook(filter);
            assertThat(partition.direct()).hasSize(1);
            assertThat(partition.direct()).containsKey(1L);
        }

        @Test
        @DisplayName("should filter by tsunami")
        void filterByTsunami() {
            DeliverySnapshot snapshot = new DeliverySnapshot(1L, List.of(
                    dc(1L, 1L, null, null, false, false, false, false, true, SeismicIntensity.ONE, "ja_jp", null),
                    dc(2L, 2L, null, null, false, false, false, false, false, SeismicIntensity.ONE, "ja_jp", null)
            ));

            ChannelFilter filter = ChannelFilter.builder().tsunami(true).build();
            DeliveryPartition partition = snapshot.getPartitionedByWebhook(filter);
            assertThat(partition.direct()).hasSize(1);
            assertThat(partition.direct()).containsKey(1L);
        }
    }

    @Nested
    @DisplayName("DeliveryChannel")
    class DeliveryChannelTests {

        @Test
        @DisplayName("isGuild should return true when guildId is not null")
        void isGuild() {
            DeliverySnapshot.DeliveryChannel ch = simple(1L, 1L, 100L, false, null);
            assertThat(ch.isGuild()).isTrue();
        }

        @Test
        @DisplayName("isGuild should return false when guildId is null")
        void isNotGuild() {
            DeliverySnapshot.DeliveryChannel ch = simple(1L, 1L, null, false, null);
            assertThat(ch.isGuild()).isFalse();
        }

        @Test
        @DisplayName("toDeliveryTarget should create target with correct fields")
        void toDeliveryTarget() {
            ChannelWebhook wh = ChannelWebhook.of(555L, "tok");
            DeliverySnapshot.DeliveryChannel ch = dc(10L, 10L, null, null, true, false, false, false, false, SeismicIntensity.ONE, "en_us", wh);
            DeliveryTarget target = ch.toDeliveryTarget(10L);

            assertThat(target.targetId()).isEqualTo(10L);
            assertThat(target.lang()).isEqualTo("en_us");
            assertThat(target.webhookUrl()).isEqualTo(wh.getUrl());
        }

        @Test
        @DisplayName("toDeliveryTarget should have null webhookUrl when no webhook")
        void toDeliveryTargetNoWebhook() {
            DeliverySnapshot.DeliveryChannel ch = simple(10L, 10L, null, false, null);
            DeliveryTarget target = ch.toDeliveryTarget(10L);

            assertThat(target.webhookUrl()).isNull();
        }
    }
}
