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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Path;
import java.util.*;
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

    // === Table ===
    private static final Table<?> DESTINATIONS = table(name("destinations"));

    // === Unqualified Fields ===
    private static final Field<Long> TARGET_ID = field(name("target_id"), Long.class);
    private static final Field<Long> CHANNEL_ID = field(name("channel_id"), Long.class);
    private static final Field<Long> THREAD_ID = field(name("thread_id"), Long.class);
    private static final Field<Long> GUILD_ID = field(name("guild_id"), Long.class);
    private static final Field<Integer> EEW_ALERT = field(name("eew_alert"), Integer.class);
    private static final Field<Integer> EEW_PREDICTION = field(name("eew_prediction"), Integer.class);
    private static final Field<Integer> EEW_DECIMATION = field(name("eew_decimation"), Integer.class);
    private static final Field<Integer> QUAKE_INFO = field(name("quake_info"), Integer.class);
    private static final Field<Integer> MIN_INTENSITY = field(name("min_intensity"), Integer.class);
    private static final Field<String> LANG = field(name("lang"), String.class);
    private static final Field<String> WEBHOOK_URL = field(name("webhook_url"), String.class);
    private static final Field<Long> WEBHOOK_ID = field(name("webhook_id"), Long.class);

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
        DataSource wrapped = wrapSqliteDataSource(ds);
        DSLContext dsl = DSL.using(wrapped, SQLDialect.SQLITE);
        Log.logger.info("Initialized SQLite channel registry at: {}", dbPath);
        return new ChannelRegistrySql(dsl, wrapped, SQLDialect.SQLITE);
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
        return this.dsl.selectFrom(DESTINATIONS)
                .where(TARGET_ID.eq(key))
                .fetchOne(this::mapToChannel);
    }

    @Override
    public void remove(long key) {
        removeWithDsl(this.dsl, key);
    }

    /**
     * Remove a channel using the provided DSLContext (for transactional use).
     */
    public void removeWithDsl(DSLContext tx, long key) {
        tx.deleteFrom(DESTINATIONS)
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
        computeIfAbsentWithDsl(this.dsl, key, mappingFunction);
    }

    /**
     * Compute if absent using the provided DSLContext (for transactional use).
     */
    public void computeIfAbsentWithDsl(DSLContext tx, long key, Function<? super Long, ? extends Channel> mappingFunction) {
        Channel channel = mappingFunction.apply(key);
        insertChannelIfAbsentWithDsl(tx, key, channel);
    }

    private void insertChannelIfAbsentWithDsl(DSLContext tx, long targetId, Channel channel) {
        tx.insertInto(DESTINATIONS)
                .columns(
                        TARGET_ID,
                        CHANNEL_ID,
                        THREAD_ID,
                        GUILD_ID,
                        EEW_ALERT,
                        EEW_PREDICTION,
                        EEW_DECIMATION,
                        QUAKE_INFO,
                        MIN_INTENSITY,
                        LANG,
                        WEBHOOK_URL,
                        WEBHOOK_ID
                )
                .values(
                        targetId,
                        channel.getChannelId(),
                        channel.getThreadId(),
                        channel.getGuildId(),
                        channel.isEewAlert() ? 1 : 0,
                        channel.isEewPrediction() ? 1 : 0,
                        channel.isEewDecimation() ? 1 : 0,
                        channel.isQuakeInfo() ? 1 : 0,
                        channel.getMinIntensity() != null ? channel.getMinIntensity().getCode() : SeismicIntensity.ONE.getCode(),
                        channel.getLang(),
                        channel.getWebhook() != null ? channel.getWebhook().getUrl() : null,
                        channel.getWebhook() != null ? channel.getWebhook().id() : null
                )
                .onConflictDoNothing()
                .execute();
    }

    @Override
    public void put(long key, Channel channel) {
        insertChannelIfAbsentWithDsl(this.dsl, key, channel);
    }

    public void putWithDsl(DSLContext tx, long key, Channel channel) {
        insertChannelIfAbsentWithDsl(tx, key, channel);
    }

    public int insertAllIfAbsent(List<Map.Entry<Long, Channel>> entries, String defaultLang) {
        return insertAllIfAbsentWithDsl(this.dsl, entries, defaultLang);
    }

    public int insertAllIfAbsentWithDsl(DSLContext tx, List<Map.Entry<Long, Channel>> entries, String defaultLang) {
        if (entries.isEmpty()) {
            return 0;
        }

        int totalInserted = 0;
        int chunkSize = 500;
        for (int i = 0; i < entries.size(); i += chunkSize) {
            List<Map.Entry<Long, Channel>> chunk = entries.subList(i, Math.min(i + chunkSize, entries.size()));

            var step = tx.insertInto(DESTINATIONS)
                    .columns(TARGET_ID, CHANNEL_ID, THREAD_ID, GUILD_ID,
                            EEW_ALERT, EEW_PREDICTION, EEW_DECIMATION, QUAKE_INFO,
                            MIN_INTENSITY, LANG, WEBHOOK_URL, WEBHOOK_ID);

            for (Map.Entry<Long, Channel> entry : chunk) {
                Channel channel = entry.getValue();
                String lang = channel.getLang() != null ? channel.getLang() : defaultLang;
                step = step.values(
                        entry.getKey(),
                        channel.getChannelId(),
                        channel.getThreadId(),
                        channel.getGuildId(),
                        channel.isEewAlert() ? 1 : 0,
                        channel.isEewPrediction() ? 1 : 0,
                        channel.isEewDecimation() ? 1 : 0,
                        channel.isQuakeInfo() ? 1 : 0,
                        channel.getMinIntensity() != null ? channel.getMinIntensity().getCode() : SeismicIntensity.ONE.getCode(),
                        lang,
                        channel.getWebhook() != null ? channel.getWebhook().getUrl() : null,
                        channel.getWebhook() != null ? channel.getWebhook().id() : null
                );
            }
            totalInserted += step.onConflictDoNothing().execute();
        }
        return totalInserted;
    }

    @Override
    public void set(long key, String name, boolean bool) {
        setWithDsl(this.dsl, key, name, bool);
    }

    /**
     * Set a boolean column using the provided DSLContext (for transactional use).
     */
    public void setWithDsl(DSLContext tx, long key, String name, boolean bool) {
        String columnName = SETTABLE_BOOLEAN_COLUMNS.get(name);
        if (columnName == null) {
            throw new IllegalArgumentException("Unknown or non-settable column: " + name);
        }

        tx.update(DESTINATIONS)
                .set(field(name(columnName), Integer.class), bool ? 1 : 0)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        setMinIntensityWithDsl(this.dsl, key, intensity);
    }

    /**
     * Set min intensity using the provided DSLContext (for transactional use).
     */
    public void setMinIntensityWithDsl(DSLContext tx, long key, SeismicIntensity intensity) {
        tx.update(DESTINATIONS)
                .set(MIN_INTENSITY, intensity.getCode())
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        setWebhookWithDsl(this.dsl, key, webhook);
    }

    /**
     * Set webhook using the provided DSLContext (for transactional use).
     */
    public void setWebhookWithDsl(DSLContext tx, long key, ChannelWebhook webhook) {
        final Long webhookId = webhook != null ? webhook.id() : null;
        tx.update(DESTINATIONS)
                .set(WEBHOOK_URL, webhook != null ? webhook.getUrl() : null)
                .set(WEBHOOK_ID, webhookId)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public void setLang(long key, String lang) {
        setLangWithDsl(this.dsl, key, lang);
    }

    /**
     * Set lang using the provided DSLContext (for transactional use).
     *
     * @throws IllegalArgumentException if lang is null
     */
    public void setLangWithDsl(DSLContext tx, long key, String lang) {
        if (lang == null) {
            throw new IllegalArgumentException("lang cannot be null");
        }
        tx.update(DESTINATIONS)
                .set(LANG, lang)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        return this.dsl.select(TARGET_ID)
                .from(DESTINATIONS)
                .where(WEBHOOK_URL.isNull())
                .fetch(0, Long.class);
    }

    @Override
    public List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
        Condition condition = buildCondition(filter);
        return this.dsl.select(TARGET_ID)
                .from(DESTINATIONS)
                .where(condition)
                .and(WEBHOOK_URL.isNull())
                .fetch(0, Long.class);
    }

    @Override
    public int removeByGuildId(long guildId) {
        return removeByGuildIdWithDsl(this.dsl, guildId);
    }

    /**
     * Remove all channels belonging to the specified guild using the provided DSLContext (for transactional use).
     */
    public int removeByGuildIdWithDsl(DSLContext tx, long guildId) {
        return tx.deleteFrom(DESTINATIONS)
                .where(GUILD_ID.eq(guildId))
                .execute();
    }

    @Override
    public int clearWebhookByUrls(Collection<String> webhookUrls) {
        return clearWebhookByUrlsWithDsl(this.dsl, webhookUrls);
    }

    public int clearWebhookByUrlsWithDsl(DSLContext tx, Collection<String> webhookUrls) {
        if (webhookUrls.isEmpty()) {
            return 0;
        }
        Set<Long> webhookIds = webhookUrls.stream()
                .map(url -> new ChannelWebhook(url).id())
                .collect(Collectors.toSet());
        return tx.update(DESTINATIONS)
                .set(WEBHOOK_URL, (String) null)
                .set(WEBHOOK_ID, (Long) null)
                .where(WEBHOOK_ID.in(webhookIds))
                .execute();
    }

    @Override
    public int setLangByGuildId(long guildId, String lang) {
        return setLangByGuildIdWithDsl(this.dsl, guildId, lang);
    }

    /**
     * Set language for all channels belonging to the specified guild.
     */
    public int setLangByGuildIdWithDsl(DSLContext tx, long guildId, String lang) {
        return tx.update(DESTINATIONS)
                .set(LANG, lang)
                .where(GUILD_ID.eq(guildId))
                .execute();
    }

    @Override
    public Map<Long, Channel> getAllChannels() {
        return getAllChannelsWithDsl(this.dsl);
    }

    /**
     * Get all channels as a map using the provided DSLContext (for transactional use).
     */
    public Map<Long, Channel> getAllChannelsWithDsl(DSLContext tx) {
        Map<Long, Channel> result = new HashMap<>();
        tx.selectFrom(DESTINATIONS)
                .fetch()
                .forEach(r -> {
                    Channel channel = mapToChannel(r);
                    if (channel != null) {
                        Long targetId = toLong(r.get(TARGET_ID));
                        if (targetId != null) {
                            result.put(targetId, channel);
                        }
                    }
                });
        return result;
    }

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        Condition condition = buildCondition(filter);

        Result<Record6<Long, Long, Long, Long, String, String>> records = this.dsl.select(
                        TARGET_ID,
                        CHANNEL_ID,
                        THREAD_ID,
                        GUILD_ID,
                        LANG,
                        WEBHOOK_URL
                )
                .from(DESTINATIONS)
                .where(condition)
                .fetch();

        Map<Boolean, List<Record6<Long, Long, Long, Long, String, String>>> partitioned = records.stream()
                .collect(Collectors.partitioningBy(r -> r.value6() != null));

        return Map.of(
                true, partitioned.get(true).stream()
                        .collect(Collectors.toMap(
                                Record6::value1,
                                r -> new ChannelBase(
                                        r.value4(),
                                        r.value2(),
                                        r.value3(),
                                        new ChannelWebhook(r.value6()),
                                        r.value5()
                                )
                        )),
                false, partitioned.get(false).stream()
                        .collect(Collectors.toMap(
                                Record6::value1,
                                r -> new ChannelBase(
                                        r.value4(),
                                        r.value2(),
                                        r.value3(),
                                        null,
                                        r.value5()
                                )
                        ))
        );
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long targetId) {
        boolean exists = this.dsl.fetchExists(
                this.dsl.selectFrom(DESTINATIONS)
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

    private static DataSource wrapSqliteDataSource(DataSource delegate) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = delegate.getConnection();
                applySqlitePragmas(connection);
                return connection;
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                Connection connection = delegate.getConnection(username, password);
                applySqlitePragmas(connection);
                return connection;
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                return delegate.unwrap(iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws SQLException {
                return delegate.isWrapperFor(iface);
            }

            @Override
            public java.io.PrintWriter getLogWriter() throws SQLException {
                return delegate.getLogWriter();
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) throws SQLException {
                delegate.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws SQLException {
                delegate.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws SQLException {
                return delegate.getLoginTimeout();
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                try {
                    return delegate.getParentLogger();
                } catch (java.sql.SQLFeatureNotSupportedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private static void applySqlitePragmas(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    /**
     * Load all channels for snapshot creation.
     */
    public List<DeliverySnapshot.DeliveryChannel> loadAllForSnapshot() {
        return loadAllForSnapshotWithDsl(this.dsl);
    }

    /**
     * Load all channels for snapshot creation using the provided DSLContext (for transactional use).
     */
    public List<DeliverySnapshot.DeliveryChannel> loadAllForSnapshotWithDsl(DSLContext tx) {
        return tx.selectFrom(DESTINATIONS)
                .fetch(this::mapToDeliveryChannel);
    }

    private DeliverySnapshot.DeliveryChannel mapToDeliveryChannel(Record r) {
        ChannelWebhook webhook = null;
        String webhookUrl = r.get(WEBHOOK_URL);
        if (webhookUrl != null) {
            webhook = new ChannelWebhook(webhookUrl);
        }

        // TARGET_ID is used as long primitive, must not be null
        Long targetId = toLong(r.get(TARGET_ID));
        Long channelId = toLong(r.get(CHANNEL_ID));

        return new DeliverySnapshot.DeliveryChannel(
                targetId != null ? targetId : 0L,
                channelId != null ? channelId : 0L,
                toLong(r.get(THREAD_ID)),
                toLong(r.get(GUILD_ID)),
                r.get(EEW_ALERT) != null && r.get(EEW_ALERT) == 1,
                r.get(EEW_PREDICTION) != null && r.get(EEW_PREDICTION) == 1,
                r.get(EEW_DECIMATION) != null && r.get(EEW_DECIMATION) == 1,
                r.get(QUAKE_INFO) != null && r.get(QUAKE_INFO) == 1,
                r.get(MIN_INTENSITY) != null ? SeismicIntensity.fromCode(r.get(MIN_INTENSITY)) : SeismicIntensity.ONE,
                r.get(LANG),
                webhook
        );
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
        String webhookUrl = r.get(WEBHOOK_URL);
        if (webhookUrl != null) {
            webhook = new ChannelWebhook(webhookUrl);
        }

        return new Channel(
                toLong(r.get(GUILD_ID)),
                toLong(r.get(CHANNEL_ID)),
                toLong(r.get(THREAD_ID)),
                r.get(EEW_ALERT) != null && r.get(EEW_ALERT) == 1,
                r.get(EEW_PREDICTION) != null && r.get(EEW_PREDICTION) == 1,
                r.get(EEW_DECIMATION) != null && r.get(EEW_DECIMATION) == 1,
                r.get(QUAKE_INFO) != null && r.get(QUAKE_INFO) == 1,
                r.get(MIN_INTENSITY) != null ? SeismicIntensity.fromCode(r.get(MIN_INTENSITY)) : SeismicIntensity.ONE,
                webhook,
                r.get(LANG)
        );
    }

    /**
     * Safely convert a Number to Long, handling SQLite's tendency to return Integer for small values.
     */
    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
