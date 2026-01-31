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

    // === Tables ===
    private static final Table<?> CHANNELS = table(name("channels"));
    private static final Table<?> CHANNEL_WEBHOOKS = table(name("channel_webhooks"));

    // JOIN用エイリアス付きテーブル
    private static final Table<?> C = CHANNELS.as("c");
    private static final Table<?> W = CHANNEL_WEBHOOKS.as("w");

    // === Unqualified Fields (単一テーブル操作用) ===
    private static final Field<Long> CHANNEL_ID = field(name("channel_id"), Long.class);
    private static final Field<Integer> IS_GUILD = field(name("is_guild"), Integer.class);
    private static final Field<Long> GUILD_ID = field(name("guild_id"), Long.class);
    private static final Field<Integer> EEW_ALERT = field(name("eew_alert"), Integer.class);
    private static final Field<Integer> EEW_PREDICTION = field(name("eew_prediction"), Integer.class);
    private static final Field<Integer> EEW_DECIMATION = field(name("eew_decimation"), Integer.class);
    private static final Field<Integer> QUAKE_INFO = field(name("quake_info"), Integer.class);
    private static final Field<Integer> MIN_INTENSITY = field(name("min_intensity"), Integer.class);
    private static final Field<String> LANG = field(name("lang"), String.class);
    private static final Field<Long> WEBHOOK_ID = field(name("webhook_id"), Long.class);
    private static final Field<String> TOKEN = field(name("token"), String.class);
    private static final Field<Long> THREAD_ID = field(name("thread_id"), Long.class);

    // === Qualified Fields for alias "c" ===
    private static final Field<Long> C_CHANNEL_ID = field(name("c", "channel_id"), Long.class);
    private static final Field<Integer> C_IS_GUILD = field(name("c", "is_guild"), Integer.class);
    private static final Field<Long> C_GUILD_ID = field(name("c", "guild_id"), Long.class);
    private static final Field<Integer> C_EEW_ALERT = field(name("c", "eew_alert"), Integer.class);
    private static final Field<Integer> C_EEW_PREDICTION = field(name("c", "eew_prediction"), Integer.class);
    private static final Field<Integer> C_EEW_DECIMATION = field(name("c", "eew_decimation"), Integer.class);
    private static final Field<Integer> C_QUAKE_INFO = field(name("c", "quake_info"), Integer.class);
    private static final Field<Integer> C_MIN_INTENSITY = field(name("c", "min_intensity"), Integer.class);
    private static final Field<String> C_LANG = field(name("c", "lang"), String.class);

    // === Qualified Fields for alias "w" ===
    private static final Field<Long> W_CHANNEL_ID = field(name("w", "channel_id"), Long.class);
    private static final Field<Long> W_WEBHOOK_ID = field(name("w", "webhook_id"), Long.class);
    private static final Field<String> W_TOKEN = field(name("w", "token"), String.class);
    private static final Field<Long> W_THREAD_ID = field(name("w", "thread_id"), Long.class);

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
        return this.dsl.select(
                        C_CHANNEL_ID,
                        C_IS_GUILD,
                        C_GUILD_ID,
                        C_EEW_ALERT,
                        C_EEW_PREDICTION,
                        C_EEW_DECIMATION,
                        C_QUAKE_INFO,
                        C_MIN_INTENSITY,
                        C_LANG,
                        W_WEBHOOK_ID,
                        W_TOKEN,
                        W_THREAD_ID
                )
                .from(C)
                .leftJoin(W).on(C_CHANNEL_ID.eq(W_CHANNEL_ID))
                .where(C_CHANNEL_ID.eq(key))
                .fetchOne(this::mapToChannel);
    }

    @Override
    public void remove(long key) {
        this.dsl.deleteFrom(CHANNELS)
                .where(CHANNEL_ID.eq(key))
                .execute();
    }

    @Override
    public boolean exists(long key) {
        return this.dsl.fetchExists(
                this.dsl.selectFrom(CHANNELS)
                        .where(CHANNEL_ID.eq(key))
        );
    }

    @Override
    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        Channel channel = mappingFunction.apply(key);
        insertChannelIfAbsent(key, channel);
    }

    private void insertChannelIfAbsent(long channelId, Channel channel) {
        int inserted = this.dsl.insertInto(CHANNELS)
                .columns(
                        CHANNEL_ID,
                        IS_GUILD,
                        GUILD_ID,
                        EEW_ALERT,
                        EEW_PREDICTION,
                        EEW_DECIMATION,
                        QUAKE_INFO,
                        MIN_INTENSITY,
                        LANG
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
        this.dsl.insertInto(CHANNEL_WEBHOOKS)
                .columns(
                        CHANNEL_ID,
                        WEBHOOK_ID,
                        TOKEN,
                        THREAD_ID
                )
                .values(
                        channelId,
                        webhook.getId(),
                        webhook.getToken(),
                        webhook.getThreadId()
                )
                .onConflict(CHANNEL_ID)
                .doUpdate()
                .set(WEBHOOK_ID, webhook.getId())
                .set(TOKEN, webhook.getToken())
                .set(THREAD_ID, webhook.getThreadId())
                .execute();
    }

    @Override
    public void set(long key, String name, boolean bool) {
        String columnName = SETTABLE_BOOLEAN_COLUMNS.get(name);
        if (columnName == null) {
            throw new IllegalArgumentException("Unknown or non-settable column: " + name);
        }

        this.dsl.update(CHANNELS)
                .set(field(name(columnName), Integer.class), bool ? 1 : 0)
                .where(CHANNEL_ID.eq(key))
                .execute();
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        this.dsl.update(CHANNELS)
                .set(MIN_INTENSITY, intensity.ordinal())
                .where(CHANNEL_ID.eq(key))
                .execute();
    }

    @Override
    public void setIsGuild(long key, boolean guild) {
        this.dsl.update(CHANNELS)
                .set(IS_GUILD, guild ? 1 : 0)
                .where(CHANNEL_ID.eq(key))
                .execute();
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        if (webhook != null) {
            upsertWebhook(key, webhook);
        } else {
            this.dsl.deleteFrom(CHANNEL_WEBHOOKS)
                    .where(CHANNEL_ID.eq(key))
                    .execute();
        }
    }

    @Override
    public void setLang(long key, String lang) {
        this.dsl.update(CHANNELS)
                .set(LANG, lang)
                .where(CHANNEL_ID.eq(key))
                .execute();
    }

    @Override
    public boolean isGuildEmpty() {
        return this.dsl.fetchExists(
                this.dsl.selectFrom(CHANNELS)
                        .where(IS_GUILD.isNull())
        );
    }

    @Override
    public void setGuildId(long channelId, long guildId) {
        this.dsl.update(CHANNELS)
                .set(GUILD_ID, guildId)
                .where(CHANNEL_ID.eq(channelId))
                .execute();
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        return this.dsl.select(C_CHANNEL_ID)
                .from(C)
                .leftJoin(W).on(C_CHANNEL_ID.eq(W_CHANNEL_ID))
                .where(W_CHANNEL_ID.isNull())
                .fetch(0, Long.class);
    }

    @Override
    public void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer) {
        Condition condition = buildCondition(filter);

        this.dsl.select(CHANNEL_ID)
                .from(CHANNELS)
                .where(condition)
                .fetch()
                .forEach(r -> consumer.accept(r.value1()));
    }

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        Condition condition = buildCondition(filter);

        Result<Record7<Long, Integer, Long, String, Long, String, Long>> records = this.dsl.select(
                        C_CHANNEL_ID,
                        C_IS_GUILD,
                        C_GUILD_ID,
                        C_LANG,
                        W_WEBHOOK_ID,
                        W_TOKEN,
                        W_THREAD_ID
                )
                .from(C)
                .leftJoin(W).on(C_CHANNEL_ID.eq(W_CHANNEL_ID))
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
        boolean exists = this.dsl.fetchExists(
                this.dsl.selectFrom(CHANNEL_WEBHOOKS)
                        .where(WEBHOOK_ID.eq(webhookId))
                        .and(THREAD_ID.isNotNull())
                        .and(THREAD_ID.ne(threadId))
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

    private Channel mapToChannel(Record r) {
        if (r == null) {
            return null;
        }

        ChannelWebhook webhook = null;
        if (r.get(W_WEBHOOK_ID) != null) {
            webhook = new ChannelWebhook(r.get(W_WEBHOOK_ID), r.get(W_TOKEN), r.get(W_THREAD_ID));
        }

        return new Channel(
                r.get(C_IS_GUILD) != null && r.get(C_IS_GUILD) == 1,
                r.get(C_GUILD_ID),
                r.get(C_EEW_ALERT) != null && r.get(C_EEW_ALERT) == 1,
                r.get(C_EEW_PREDICTION) != null && r.get(C_EEW_PREDICTION) == 1,
                r.get(C_EEW_DECIMATION) != null && r.get(C_EEW_DECIMATION) == 1,
                r.get(C_QUAKE_INFO) != null && r.get(C_QUAKE_INFO) == 1,
                r.get(C_MIN_INTENSITY) != null ? SeismicIntensity.values()[r.get(C_MIN_INTENSITY)] : SeismicIntensity.ONE,
                webhook,
                r.get(C_LANG)
        );
    }
}
