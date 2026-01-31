package net.teamfruit.eewbot.registry.channel;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

public class ChannelRegistrySql implements ChannelRegistry {

    private static final Map<String, String> SETTABLE_BOOLEAN_COLUMNS = Map.of(
            "eewAlert", "eew_alert",
            "eewPrediction", "eew_prediction",
            "eewDecimation", "eew_decimation",
            "quakeInfo", "quake_info"
    );

    private final DSLContext dsl;
    private final DataSource dataSource;
    private final SQLDialect dialect;

    private ChannelRegistrySql(DSLContext dsl, DataSource dataSource, SQLDialect dialect) {
        this.dsl = dsl;
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public static ChannelRegistrySql forSQLite(Path dbPath) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath);
        DSLContext dsl = DSL.using(ds, SQLDialect.SQLITE);
        Log.logger.info("Initialized SQLite channel registry at: {}", dbPath);
        return new ChannelRegistrySql(dsl, ds, SQLDialect.SQLITE);
    }

    public static ChannelRegistrySql forPostgreSQL(ConfigV2.PostgreSQL config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        DSLContext dsl = DSL.using(ds, SQLDialect.POSTGRES);
        Log.logger.info("Initialized PostgreSQL channel registry at: {}", config.getJdbcUrl());
        return new ChannelRegistrySql(dsl, ds, SQLDialect.POSTGRES);
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    public SQLDialect getDialect() {
        return this.dialect;
    }

    public DSLContext getDsl() {
        return this.dsl;
    }

    @Override
    public Channel get(long key) {
        Table<?> channels = table(name("channels"));
        Table<?> channelWebhooks = table(name("channel_webhooks"));

        Field<Long> channelId = field(name("channel_id"), Long.class);
        Field<Integer> isGuild = field(name("is_guild"), Integer.class);
        Field<Long> guildId = field(name("guild_id"), Long.class);
        Field<Integer> eewAlert = field(name("eew_alert"), Integer.class);
        Field<Integer> eewPrediction = field(name("eew_prediction"), Integer.class);
        Field<Integer> eewDecimation = field(name("eew_decimation"), Integer.class);
        Field<Integer> quakeInfo = field(name("quake_info"), Integer.class);
        Field<Integer> minIntensity = field(name("min_intensity"), Integer.class);
        Field<String> lang = field(name("lang"), String.class);
        Field<Long> webhookId = field(name("webhook_id"), Long.class);
        Field<String> token = field(name("token"), String.class);
        Field<Long> threadId = field(name("thread_id"), Long.class);

        return this.dsl.select(
                        channelId,
                        isGuild,
                        guildId,
                        eewAlert,
                        eewPrediction,
                        eewDecimation,
                        quakeInfo,
                        minIntensity,
                        lang,
                        webhookId,
                        token,
                        threadId
                )
                .from(channels)
                .leftJoin(channelWebhooks).on(field(name(channels.getName(), "channel_id")).eq(field(name(channelWebhooks.getName(), "channel_id"))))
                .where(field(name(channels.getName(), "channel_id")).eq(key))
                .fetchOne(r -> mapToChannel(r, isGuild, guildId, eewAlert, eewPrediction, eewDecimation, quakeInfo, minIntensity, lang, webhookId, token, threadId));
    }

    @Override
    public void remove(long key) {
        Table<?> channels = table(name("channels"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        this.dsl.deleteFrom(channels)
                .where(channelId.eq(key))
                .execute();
    }

    @Override
    public boolean exists(long key) {
        Table<?> channels = table(name("channels"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        return this.dsl.fetchExists(
                this.dsl.selectFrom(channels)
                        .where(channelId.eq(key))
        );
    }

    @Override
    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        Channel channel = mappingFunction.apply(key);
        insertChannelIfAbsent(key, channel);
    }

    private void insertChannelIfAbsent(long channelId, Channel channel) {
        Table<?> channels = table(name("channels"));

        int inserted = this.dsl.insertInto(channels)
                .columns(
                        field(name("channel_id")),
                        field(name("is_guild")),
                        field(name("guild_id")),
                        field(name("eew_alert")),
                        field(name("eew_prediction")),
                        field(name("eew_decimation")),
                        field(name("quake_info")),
                        field(name("min_intensity")),
                        field(name("lang"))
                )
                .values(
                        channelId,
                        channel.isGuild() ? 1 : 0,
                        channel.getGuildId(),
                        channel.isEewAlert() ? 1 : 0,
                        channel.isEewPrediction() ? 1 : 0,
                        channel.isEewDecimation() ? 1 : 0,
                        channel.isQuakeInfo() ? 1 : 0,
                        channel.getMinIntensity() != null ? channel.getMinIntensity().ordinal() : SeismicIntensity.ONE.ordinal(),
                        channel.getLang()
                )
                .onConflictDoNothing()
                .execute();

        if (inserted > 0 && channel.getWebhook() != null) {
            upsertWebhook(channelId, channel.getWebhook());
        }
    }

    private void upsertWebhook(long channelId, ChannelWebhook webhook) {
        Table<?> channelWebhooks = table(name("channel_webhooks"));

        this.dsl.insertInto(channelWebhooks)
                .columns(
                        field(name("channel_id")),
                        field(name("webhook_id")),
                        field(name("token")),
                        field(name("thread_id"))
                )
                .values(
                        channelId,
                        webhook.getId(),
                        webhook.getToken(),
                        webhook.getThreadId()
                )
                .onConflict(field(name("channel_id")))
                .doUpdate()
                .set(field(name("webhook_id")), webhook.getId())
                .set(field(name("token")), webhook.getToken())
                .set(field(name("thread_id")), webhook.getThreadId())
                .execute();
    }

    @Override
    public void set(long key, String name, boolean bool) {
        String columnName = SETTABLE_BOOLEAN_COLUMNS.get(name);
        if (columnName == null) {
            throw new IllegalArgumentException("Unknown or non-settable column: " + name);
        }

        Table<?> channels = table(name("channels"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        this.dsl.update(channels)
                .set(field(name(columnName), Integer.class), bool ? 1 : 0)
                .where(channelId.eq(key))
                .execute();
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        Table<?> channels = table(name("channels"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        this.dsl.update(channels)
                .set(field(name("min_intensity")), intensity.ordinal())
                .where(channelId.eq(key))
                .execute();
    }

    @Override
    public void setIsGuild(long key, boolean guild) {
        Table<?> channels = table(name("channels"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        this.dsl.update(channels)
                .set(field(name("is_guild")), guild ? 1 : 0)
                .where(channelId.eq(key))
                .execute();
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        if (webhook != null) {
            upsertWebhook(key, webhook);
        } else {
            Table<?> channelWebhooks = table(name("channel_webhooks"));
            Field<Long> channelId = field(name("channel_id"), Long.class);

            this.dsl.deleteFrom(channelWebhooks)
                    .where(channelId.eq(key))
                    .execute();
        }
    }

    @Override
    public void setLang(long key, String lang) {
        Table<?> channels = table(name("channels"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        this.dsl.update(channels)
                .set(field(name("lang")), lang)
                .where(channelId.eq(key))
                .execute();
    }

    @Override
    public boolean isGuildEmpty() {
        Table<?> channels = table(name("channels"));
        Field<Integer> isGuild = field(name("is_guild"), Integer.class);

        return this.dsl.fetchExists(
                this.dsl.selectFrom(channels)
                        .where(isGuild.isNull())
        );
    }

    @Override
    public void setGuildId(long channelId, long guildId) {
        Table<?> channels = table(name("channels"));
        Field<Long> channelIdField = field(name("channel_id"), Long.class);

        this.dsl.update(channels)
                .set(field(name("guild_id")), guildId)
                .where(channelIdField.eq(channelId))
                .execute();
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        Table<?> channels = table(name("channels"));
        Table<?> channelWebhooks = table(name("channel_webhooks"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        return this.dsl.select(field(name(channels.getName(), "channel_id"), Long.class))
                .from(channels)
                .leftJoin(channelWebhooks).on(field(name(channels.getName(), "channel_id")).eq(field(name(channelWebhooks.getName(), "channel_id"))))
                .where(field(name(channelWebhooks.getName(), "channel_id")).isNull())
                .fetch(0, Long.class);
    }

    @Override
    public void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer) {
        Table<?> channels = table(name("channels"));
        Field<Long> channelId = field(name("channel_id"), Long.class);

        Condition condition = buildCondition(filter);

        this.dsl.select(channelId)
                .from(channels)
                .where(condition)
                .fetch()
                .forEach(r -> consumer.accept(r.value1()));
    }

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        Table<?> channels = table(name("channels"));
        Table<?> channelWebhooks = table(name("channel_webhooks"));

        Field<Long> channelId = field(name("channel_id"), Long.class);
        Field<Integer> isGuild = field(name("is_guild"), Integer.class);
        Field<Long> guildId = field(name("guild_id"), Long.class);
        Field<Integer> eewAlert = field(name("eew_alert"), Integer.class);
        Field<Integer> eewPrediction = field(name("eew_prediction"), Integer.class);
        Field<Integer> eewDecimation = field(name("eew_decimation"), Integer.class);
        Field<Integer> quakeInfo = field(name("quake_info"), Integer.class);
        Field<Integer> minIntensity = field(name("min_intensity"), Integer.class);
        Field<String> lang = field(name("lang"), String.class);
        Field<Long> webhookId = field(name("webhook_id"), Long.class);
        Field<String> token = field(name("token"), String.class);
        Field<Long> threadId = field(name("thread_id"), Long.class);

        Condition condition = buildCondition(filter);

        Result<Record7<Long, Integer, Long, String, Long, String, Long>> records = this.dsl.select(
                        field(name(channels.getName(), "channel_id"), Long.class),
                        field(name(channels.getName(), "is_guild"), Integer.class),
                        field(name(channels.getName(), "guild_id"), Long.class),
                        field(name(channels.getName(), "lang"), String.class),
                        field(name(channelWebhooks.getName(), "webhook_id"), Long.class),
                        field(name(channelWebhooks.getName(), "token"), String.class),
                        field(name(channelWebhooks.getName(), "thread_id"), Long.class)
                )
                .from(channels)
                .leftJoin(channelWebhooks).on(field(name(channels.getName(), "channel_id")).eq(field(name(channelWebhooks.getName(), "channel_id"))))
                .where(condition)
                .fetch();

        Map<Boolean, List<Record7<Long, Integer, Long, String, Long, String, Long>>> partitioned = records.stream()
                .collect(Collectors.partitioningBy(r -> r.value5() != null));

        return Map.of(
                true, partitioned.get(true).stream()
                        .collect(Collectors.toMap(
                                r -> r.value1(),
                                r -> new ChannelBase(
                                        r.value2() != null && r.value2() == 1,
                                        r.value3(),
                                        new ChannelWebhook(r.value5(), r.value6(), r.value7()),
                                        r.value4()
                                )
                        )),
                false, partitioned.get(false).stream()
                        .collect(Collectors.toMap(
                                r -> r.value1(),
                                r -> new ChannelBase(
                                        r.value2() != null && r.value2() == 1,
                                        r.value3(),
                                        null,
                                        r.value4()
                                )
                        ))
        );
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long threadId) {
        Table<?> channelWebhooks = table(name("channel_webhooks"));
        Field<Long> webhookIdField = field(name("webhook_id"), Long.class);
        Field<Long> threadIdField = field(name("thread_id"), Long.class);

        boolean exists = this.dsl.fetchExists(
                this.dsl.selectFrom(channelWebhooks)
                        .where(webhookIdField.eq(webhookId))
                        .and(threadIdField.isNotNull())
                        .and(threadIdField.ne(threadId))
        );
        return !exists;
    }

    public void close() {
        if (this.dataSource instanceof HikariDataSource) {
            ((HikariDataSource) this.dataSource).close();
            Log.logger.info("Closed PostgreSQL connection pool");
        }
    }

    private Condition buildCondition(ChannelFilter filter) {
        if (filter == null) {
            return noCondition();
        }
        return filter.toCondition();
    }

    private Channel mapToChannel(Record r,
                                  Field<Integer> isGuild,
                                  Field<Long> guildId,
                                  Field<Integer> eewAlert,
                                  Field<Integer> eewPrediction,
                                  Field<Integer> eewDecimation,
                                  Field<Integer> quakeInfo,
                                  Field<Integer> minIntensity,
                                  Field<String> lang,
                                  Field<Long> webhookId,
                                  Field<String> token,
                                  Field<Long> threadId) {
        if (r == null) {
            return null;
        }

        ChannelWebhook webhook = null;
        if (r.get(webhookId) != null) {
            webhook = new ChannelWebhook(r.get(webhookId), r.get(token), r.get(threadId));
        }

        return new Channel(
                r.get(isGuild) != null && r.get(isGuild) == 1,
                r.get(guildId),
                r.get(eewAlert) != null && r.get(eewAlert) == 1,
                r.get(eewPrediction) != null && r.get(eewPrediction) == 1,
                r.get(eewDecimation) != null && r.get(eewDecimation) == 1,
                r.get(quakeInfo) != null && r.get(quakeInfo) == 1,
                r.get(minIntensity) != null ? SeismicIntensity.values()[r.get(minIntensity)] : SeismicIntensity.ONE,
                webhook,
                r.get(lang)
        );
    }
}
