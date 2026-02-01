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
    private static final Table<?> DESTINATIONS = table(name("destinations"));
    private static final Table<?> DESTINATION_WEBHOOKS = table(name("destination_webhooks"));

    // JOIN用エイリアス付きテーブル
    private static final Table<?> D = DESTINATIONS.as("d");
    private static final Table<?> W = DESTINATION_WEBHOOKS.as("w");

    // === Unqualified Fields (単一テーブル操作用) ===
    private static final Field<Long> TARGET_ID = field(name("target_id"), Long.class);
    private static final Field<Long> CHANNEL_ID = field(name("channel_id"), Long.class);
    private static final Field<Long> THREAD_ID = field(name("thread_id"), Long.class);
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

    // === Qualified Fields for alias "d" ===
    private static final Field<Long> D_TARGET_ID = field(name("d", "target_id"), Long.class);
    private static final Field<Long> D_CHANNEL_ID = field(name("d", "channel_id"), Long.class);
    private static final Field<Long> D_THREAD_ID = field(name("d", "thread_id"), Long.class);
    private static final Field<Integer> D_IS_GUILD = field(name("d", "is_guild"), Integer.class);
    private static final Field<Long> D_GUILD_ID = field(name("d", "guild_id"), Long.class);
    private static final Field<Integer> D_EEW_ALERT = field(name("d", "eew_alert"), Integer.class);
    private static final Field<Integer> D_EEW_PREDICTION = field(name("d", "eew_prediction"), Integer.class);
    private static final Field<Integer> D_EEW_DECIMATION = field(name("d", "eew_decimation"), Integer.class);
    private static final Field<Integer> D_QUAKE_INFO = field(name("d", "quake_info"), Integer.class);
    private static final Field<Integer> D_MIN_INTENSITY = field(name("d", "min_intensity"), Integer.class);
    private static final Field<String> D_LANG = field(name("d", "lang"), String.class);

    // === Qualified Fields for alias "w" ===
    private static final Field<Long> W_TARGET_ID = field(name("w", "target_id"), Long.class);
    private static final Field<Long> W_WEBHOOK_ID = field(name("w", "webhook_id"), Long.class);
    private static final Field<String> W_TOKEN = field(name("w", "token"), String.class);

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
                        D_TARGET_ID,
                        D_CHANNEL_ID,
                        D_THREAD_ID,
                        D_IS_GUILD,
                        D_GUILD_ID,
                        D_EEW_ALERT,
                        D_EEW_PREDICTION,
                        D_EEW_DECIMATION,
                        D_QUAKE_INFO,
                        D_MIN_INTENSITY,
                        D_LANG,
                        W_WEBHOOK_ID,
                        W_TOKEN
                )
                .from(D)
                .leftJoin(W).on(D_TARGET_ID.eq(W_TARGET_ID))
                .where(D_TARGET_ID.eq(key))
                .fetchOne(this::mapToChannel);
    }

    @Override
    public void remove(long key) {
        this.dsl.deleteFrom(DESTINATIONS)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public boolean exists(long key) {
        return this.dsl.fetchExists(
                this.dsl.selectFrom(DESTINATIONS)
                        .where(TARGET_ID.eq(key))
        );
    }

    @Override
    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        Channel channel = mappingFunction.apply(key);
        insertChannelIfAbsent(key, channel);
    }

    private void insertChannelIfAbsent(long targetId, Channel channel) {
        int inserted = this.dsl.insertInto(DESTINATIONS)
                .columns(
                        TARGET_ID,
                        CHANNEL_ID,
                        THREAD_ID,
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
                        targetId,
                        channel.getChannelId(),
                        channel.getThreadId(),
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
            upsertWebhook(targetId, channel.getWebhook());
        }
    }

    private void upsertWebhook(long targetId, ChannelWebhook webhook) {
        this.dsl.insertInto(DESTINATION_WEBHOOKS)
                .columns(
                        TARGET_ID,
                        WEBHOOK_ID,
                        TOKEN
                )
                .values(
                        targetId,
                        webhook.getId(),
                        webhook.getToken()
                )
                .onConflict(TARGET_ID)
                .doUpdate()
                .set(WEBHOOK_ID, webhook.getId())
                .set(TOKEN, webhook.getToken())
                .execute();
    }

    @Override
    public void set(long key, String name, boolean bool) {
        String columnName = SETTABLE_BOOLEAN_COLUMNS.get(name);
        if (columnName == null) {
            throw new IllegalArgumentException("Unknown or non-settable column: " + name);
        }

        this.dsl.update(DESTINATIONS)
                .set(field(name(columnName), Integer.class), bool ? 1 : 0)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        this.dsl.update(DESTINATIONS)
                .set(MIN_INTENSITY, intensity.ordinal())
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public void setIsGuild(long key, boolean guild) {
        this.dsl.update(DESTINATIONS)
                .set(IS_GUILD, guild ? 1 : 0)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        if (webhook != null) {
            upsertWebhook(key, webhook);
        } else {
            this.dsl.deleteFrom(DESTINATION_WEBHOOKS)
                    .where(TARGET_ID.eq(key))
                    .execute();
        }
    }

    @Override
    public void setLang(long key, String lang) {
        this.dsl.update(DESTINATIONS)
                .set(LANG, lang)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public boolean isGuildEmpty() {
        return this.dsl.fetchExists(
                this.dsl.selectFrom(DESTINATIONS)
                        .where(IS_GUILD.isNull())
        );
    }

    @Override
    public void setGuildId(long targetId, long guildId) {
        this.dsl.update(DESTINATIONS)
                .set(GUILD_ID, guildId)
                .where(TARGET_ID.eq(targetId))
                .execute();
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        return this.dsl.select(D_TARGET_ID)
                .from(D)
                .leftJoin(W).on(D_TARGET_ID.eq(W_TARGET_ID))
                .where(W_TARGET_ID.isNull())
                .fetch(0, Long.class);
    }

    @Override
    public void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer) {
        Condition condition = buildCondition(filter);

        this.dsl.select(TARGET_ID)
                .from(DESTINATIONS)
                .where(condition)
                .fetch()
                .forEach(r -> consumer.accept(r.value1()));
    }

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        Condition condition = buildCondition(filter);

        Result<Record8<Long, Long, Long, Integer, Long, String, Long, String>> records = this.dsl.select(
                        D_TARGET_ID,
                        D_CHANNEL_ID,
                        D_THREAD_ID,
                        D_IS_GUILD,
                        D_GUILD_ID,
                        D_LANG,
                        W_WEBHOOK_ID,
                        W_TOKEN
                )
                .from(D)
                .leftJoin(W).on(D_TARGET_ID.eq(W_TARGET_ID))
                .where(condition)
                .fetch();

        Map<Boolean, List<Record8<Long, Long, Long, Integer, Long, String, Long, String>>> partitioned = records.stream()
                .collect(Collectors.partitioningBy(r -> r.value7() != null));

        return Map.of(
                true, partitioned.get(true).stream()
                        .collect(Collectors.toMap(
                                r -> r.value1(),
                                r -> new ChannelBase(
                                        r.value4() != null && r.value4() == 1,
                                        r.value5(),
                                        r.value2(),
                                        r.value3(),
                                        new ChannelWebhook(r.value7(), r.value8()),
                                        r.value6()
                                )
                        )),
                false, partitioned.get(false).stream()
                        .collect(Collectors.toMap(
                                r -> r.value1(),
                                r -> new ChannelBase(
                                        r.value4() != null && r.value4() == 1,
                                        r.value5(),
                                        r.value2(),
                                        r.value3(),
                                        null,
                                        r.value6()
                                )
                        ))
        );
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long targetId) {
        boolean exists = this.dsl.fetchExists(
                this.dsl.selectFrom(DESTINATION_WEBHOOKS)
                        .where(WEBHOOK_ID.eq(webhookId))
                        .and(TARGET_ID.ne(targetId))
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
            webhook = new ChannelWebhook(r.get(W_WEBHOOK_ID), r.get(W_TOKEN));
        }

        return new Channel(
                r.get(D_IS_GUILD) != null && r.get(D_IS_GUILD) == 1,
                r.get(D_GUILD_ID),
                r.get(D_CHANNEL_ID),
                r.get(D_THREAD_ID),
                r.get(D_EEW_ALERT) != null && r.get(D_EEW_ALERT) == 1,
                r.get(D_EEW_PREDICTION) != null && r.get(D_EEW_PREDICTION) == 1,
                r.get(D_EEW_DECIMATION) != null && r.get(D_EEW_DECIMATION) == 1,
                r.get(D_QUAKE_INFO) != null && r.get(D_QUAKE_INFO) == 1,
                r.get(D_MIN_INTENSITY) != null ? SeismicIntensity.values()[r.get(D_MIN_INTENSITY)] : SeismicIntensity.ONE,
                webhook,
                r.get(D_LANG)
        );
    }
}
