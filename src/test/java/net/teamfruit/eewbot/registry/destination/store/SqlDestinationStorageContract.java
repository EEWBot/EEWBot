package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationCleanup;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/** Runs the same storage contract against SQLite and PostgreSQL. */
abstract class SqlDestinationStorageContract {

    protected ChannelRegistrySql sql;
    protected ConfigRevisionStore revisions;
    protected DestinationConfigSource source;
    protected DestinationAdminRegistry admin;
    protected AtomicInteger callbacks;

    abstract ChannelRegistrySql openRegistry() throws IOException;

    @BeforeEach
    void initialize() throws IOException {
        this.sql = openRegistry();
        DatabaseInitializer.migrate(this.sql.getDataSource(), this.sql.getDialect());
        this.sql.getDsl().execute("DELETE FROM destinations");
        this.sql.getDsl().execute("UPDATE config_meta SET channels_revision = 0 WHERE id = 1");
        this.revisions = new ConfigRevisionStore(this.sql.getDsl(), this.sql.getDialect());
        this.source = new SqlDestinationConfigSource(this.sql, this.revisions);
        this.callbacks = new AtomicInteger();
        this.admin = new SqlAdminRegistry(this.sql, this.revisions, this.callbacks::incrementAndGet);
    }

    @AfterEach
    void close() {
        if (this.sql != null) this.sql.close();
    }

    @Test
    void sourceInterfaceReadsRevisionAndSnapshot() {
        assertThat(this.source.getRevision()).isZero();
        assertThat(this.source.loadSnapshot().size()).isZero();
        this.admin.put(1, Channel.createDefault(10L, 1L, null, "ja_jp"));

        DeliverySnapshot snapshot = this.source.loadSnapshot();
        assertThat(snapshot.getRevision()).isEqualTo(this.source.getRevision()).isEqualTo(1);
        assertThat(snapshot.get(1).guildId()).isEqualTo(10);
    }

    @Test
    void failedRevisionIncrementRollsBackBothConfigurationAndRevision() {
        this.admin.put(1, Channel.createDefault(10L, 1L, null, "ja_jp"));
        this.callbacks.set(0);
        ConfigRevisionStore failingRevision = new ConfigRevisionStore(this.sql.getDsl(), this.sql.getDialect()) {
            @Override
            public long incrementWithDsl(DSLContext tx) {
                super.incrementWithDsl(tx);
                throw new IllegalStateException("failure after revision update");
            }
        };
        DestinationAdminRegistry failingAdmin = new SqlAdminRegistry(this.sql, failingRevision, this.callbacks::incrementAndGet);

        assertThatThrownBy(() -> failingAdmin.setLang(1, "en_us")).isInstanceOf(IllegalStateException.class);

        assertThat(this.admin.get(1).getLang()).isEqualTo("ja_jp");
        assertThat(this.source.getRevision()).isEqualTo(1);
        assertThat(this.callbacks.get()).isZero();
    }

    @Test
    void callbackCanReadCommittedConfigurationFromAnotherConnection() {
        DestinationAdminRegistry writer = new SqlAdminRegistry(this.sql, this.revisions, () -> {
            DeliverySnapshot committed = this.source.loadSnapshot();
            assertThat(committed.getRevision()).isEqualTo(1);
            assertThat(committed.get(1).lang()).isEqualTo("en_us");
            this.callbacks.incrementAndGet();
        });

        writer.put(1, Channel.createDefault(null, 1L, null, "en_us"));

        assertThat(this.callbacks.get()).isEqualTo(1);
    }

    @Test
    void insertIfAbsentAndZeroRowWritesDoNotIncrementRevision() {
        this.admin.put(1, Channel.createDefault(10L, 1L, null, "ja_jp"));
        this.callbacks.set(0);

        this.admin.put(1, Channel.createDefault(null, 1L, null, "en_us"));
        this.admin.remove(999);
        this.admin.setLang(999, "en_us");
        this.admin.setAll(1, Map.of());
        assertThat(this.admin.removeByGuildId(999)).isZero();
        assertThat(this.admin.clearWebhookByUrls(List.of())).isZero();

        assertThat(this.source.getRevision()).isEqualTo(1);
        assertThat(this.callbacks.get()).isZero();
        assertThat(this.admin.get(1).getLang()).isEqualTo("ja_jp");
    }

    @Test
    void batchWriteIncrementsRevisionOnce() {
        this.admin.put(1, Channel.createDefault(10L, 1L, null, "ja_jp"));
        this.admin.put(2, Channel.createDefault(10L, 2L, null, "ja_jp"));
        this.callbacks.set(0);

        assertThat(this.admin.setLangByGuildId(10, "en_us")).isEqualTo(2);

        assertThat(this.source.getRevision()).isEqualTo(3);
        assertThat(this.callbacks.get()).isEqualTo(1);
    }

    @Test
    void snowflakesFlagsNullsAndWebhookThreadParametersSurviveSnapshot() {
        long guild = 1234567890123456789L;
        long parent = 1234567890123456790L;
        long thread = 1234567890123456791L;
        long webhookId = 1234567890123456792L;
        ChannelWebhook webhook = new ChannelWebhook("https://discord.com/api/webhooks/" + webhookId + "/test?thread_id=" + thread);
        this.admin.put(thread, new Channel(guild, parent, thread, true, false, true, false,
                true, SeismicIntensity.FOUR, webhook, "en_us"));
        this.admin.put(1, Channel.createDefault(null, 1L, null, "ja_jp"));

        var destination = this.source.loadSnapshot().get(thread);
        assertThat(destination.guildId()).isEqualTo(guild);
        assertThat(destination.channelId()).isEqualTo(parent);
        assertThat(destination.threadId()).isEqualTo(thread);
        assertThat(destination.targetId()).isEqualTo(thread);
        assertThat(destination.webhook().id()).isEqualTo(webhookId);
        assertThat(destination.webhook().getUrl()).isEqualTo(webhook.getUrl());
        assertThat(destination.eewAlert()).isTrue();
        assertThat(destination.eewPrediction()).isFalse();
        assertThat(destination.eewDecimation()).isTrue();
        assertThat(destination.quakeInfo()).isFalse();
        assertThat(destination.tsunami()).isTrue();
        assertThat(destination.minIntensity()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(destination.lang()).isEqualTo("en_us");
        assertThat(this.source.loadSnapshot().get(1).guildId()).isNull();
        assertThat(this.source.loadSnapshot().get(1).threadId()).isNull();
        assertThat(this.source.loadSnapshot().get(1).webhook()).isNull();
    }

    @Test
    void cleanupRetainsExistingGuildAndTargetDeletionSemantics() throws IOException {
        this.admin.put(1, Channel.createDefault(10L, 1L, null, "ja_jp"));
        this.admin.put(2, Channel.createDefault(10L, 1L, 2L, "ja_jp"));
        this.admin.put(3, Channel.createDefault(20L, 3L, null, "ja_jp"));
        DestinationCleanup cleanup = DestinationCleanup.using(this.admin);

        cleanup.destinationDeleted(1);
        assertThat(this.admin.exists(1)).isFalse();
        assertThat(this.admin.exists(2)).isTrue();
        cleanup.destinationDeleted(2);
        assertThat(this.admin.exists(2)).isFalse();
        cleanup.guildDeleted(20);
        assertThat(this.source.loadSnapshot().size()).isZero();
        assertThat(this.source.getRevision()).isEqualTo(6);
    }

    @Test
    void sqlChannelAndSnapshotFiltersSelectTheSameDestinations() {
        this.admin.put(1, Channel.createDefault(null, 1L, null, "ja_jp"));
        this.admin.put(2, new Channel(10L, 2L, null, true, false, true, false,
                true, SeismicIntensity.FOUR, ChannelWebhook.of(1234567890123456789L, "test"), "en_us"));
        this.admin.put(3, new Channel(20L, 2L, 3L, false, true, false, true,
                false, SeismicIntensity.ONE, null, "ja_jp"));
        DeliverySnapshot snapshot = this.source.loadSnapshot();
        List<ChannelFilter> filters = List.of(
                ChannelFilter.builder().build(),
                ChannelFilter.builder().hasGuild(true).build(), ChannelFilter.builder().hasGuild(false).build(),
                ChannelFilter.builder().guildId(10).build(), ChannelFilter.builder().channelId(2).build(),
                ChannelFilter.builder().threadId(null).build(), ChannelFilter.builder().threadId(3L).build(),
                ChannelFilter.builder().isThread(true).build(), ChannelFilter.builder().isThread(false).build(),
                ChannelFilter.builder().eewAlert(true).build(), ChannelFilter.builder().eewAlert(false).build(),
                ChannelFilter.builder().eewPrediction(true).build(), ChannelFilter.builder().eewPrediction(false).build(),
                ChannelFilter.builder().eewDecimation(true).build(), ChannelFilter.builder().eewDecimation(false).build(),
                ChannelFilter.builder().quakeInfo(true).build(), ChannelFilter.builder().quakeInfo(false).build(),
                ChannelFilter.builder().tsunami(true).build(), ChannelFilter.builder().tsunami(false).build(),
                ChannelFilter.builder().intensity(SeismicIntensity.THREE).build(),
                ChannelFilter.builder().webhookId(1234567890123456789L).build(),
                ChannelFilter.builder().hasGuild(true).isThread(true).quakeInfo(true).build());
        for (ChannelFilter filter : filters) {
            var sqlPartition = this.sql.getChannelsPartitionedByWebhookPresent(filter);
            var snapshotPartition = snapshot.getPartitionedByWebhook(filter);
            assertThat(snapshotPartition).isEqualTo(sqlPartition);
            var memoryIds = this.admin.getAllChannels().entrySet().stream()
                    .filter(entry -> filter.test(entry.getValue())).map(Map.Entry::getKey).toList();
            assertThat(Stream.concat(snapshotPartition.direct().keySet().stream(), snapshotPartition.webhook().keySet().stream()).toList())
                    .containsExactlyInAnyOrderElementsOf(memoryIds);
        }
        assertThat(snapshot.getPartitionedByWebhook(null)).isEqualTo(this.sql.getChannelsPartitionedByWebhookPresent(null));
    }
}
