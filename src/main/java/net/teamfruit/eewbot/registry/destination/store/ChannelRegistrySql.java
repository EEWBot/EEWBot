package net.teamfruit.eewbot.registry.destination.store;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

public class ChannelRegistrySql implements net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry {

    /** Must match the DB schema DEFAULT for the lang column. */
    private static final String DEFAULT_LANG = "ja_jp";

    private static final Map<String, String> SETTABLE_BOOLEAN_COLUMNS = Map.of(
            "eewAlert", "eew_alert",
            "eewPrediction", "eew_prediction",
            "eewDecimation", "eew_decimation",
            "quakeInfo", "quake_info"
    );

    // === Table ===
    private static final Table<?> DESTINATIONS = table(name("destinations"));

    // === Unqualified Fields ===
    private static final Field<Long> TARGET_ID = field(name("target_id"), SQLDataType.BIGINT);
    private static final Field<Long> CHANNEL_ID = field(name("channel_id"), SQLDataType.BIGINT);
    private static final Field<Long> THREAD_ID = field(name("thread_id"), SQLDataType.BIGINT);
    private static final Field<Long> GUILD_ID = field(name("guild_id"), SQLDataType.BIGINT);
    private static final Field<Integer> EEW_ALERT = field(name("eew_alert"), Integer.class);
    private static final Field<Integer> EEW_PREDICTION = field(name("eew_prediction"), Integer.class);
    private static final Field<Integer> EEW_DECIMATION = field(name("eew_decimation"), Integer.class);
    private static final Field<Integer> QUAKE_INFO = field(name("quake_info"), Integer.class);
    private static final Field<Integer> MIN_INTENSITY = field(name("min_intensity"), Integer.class);
    private static final Field<String> LANG = field(name("lang"), String.class);
    private static final Field<String> WEBHOOK_URL = field(name("webhook_url"), String.class);
    private static final Field<Long> WEBHOOK_ID = field(name("webhook_id"), SQLDataType.BIGINT);

    /** All destination fields for typed SELECT (avoids SQLite INTEGER->Integer truncation in selectFrom). */
    private static final SelectFieldOrAsterisk[] ALL_FIELDS = {
            TARGET_ID, CHANNEL_ID, THREAD_ID, GUILD_ID,
            EEW_ALERT, EEW_PREDICTION, EEW_DECIMATION, QUAKE_INFO,
            MIN_INTENSITY, LANG, WEBHOOK_URL, WEBHOOK_ID
    };

    private final DSLContext dsl;
    private final DataSource dataSource;
    private final SQLDialect dialect;

    private ChannelRegistrySql(DSLContext dsl, DataSource dataSource, SQLDialect dialect) {
        this.dsl = dsl;
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public static ChannelRegistrySql forSQLite(Path dbPath) throws IOException {
        Path parent = dbPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
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
        hikariConfig.setLeakDetectionThreshold(60000);

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
        return this.dsl.select(ALL_FIELDS).from(DESTINATIONS)
                .where(TARGET_ID.eq(key))
                .fetchOne(this::mapToChannel);
    }

    @Override
    public void remove(long key) {
        removeWithDsl(this.dsl, key);
    }

    /**
     * Remove a channel using the provided DSLContext (for transactional use).
     *
     * @return number of rows deleted
     */
    public int removeWithDsl(DSLContext tx, long key) {
        return tx.deleteFrom(DESTINATIONS)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public boolean exists(long key) {
        return this.dsl.fetchExists(
                this.dsl.selectOne().from(DESTINATIONS)
                        .where(TARGET_ID.eq(key))
        );
    }

    /**
     * Insert a channel if absent (on conflict do nothing).
     *
     * @return number of rows inserted (0 if already exists, 1 if inserted)
     */
    int insertChannelIfAbsentWithDsl(DSLContext tx, long targetId, Channel channel) {
        return tx.insertInto(DESTINATIONS)
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
                        channel.getLang() != null ? channel.getLang() : DEFAULT_LANG,
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

    public int putWithDsl(DSLContext tx, long key, Channel channel) {
        return insertChannelIfAbsentWithDsl(tx, key, channel);
    }

    @Override
    public void putAll(Map<Long, Channel> channels) {
        putAllWithDsl(this.dsl, channels);
    }

    public int putAllWithDsl(DSLContext tx, Map<Long, Channel> channels) {
        int total = 0;
        for (Map.Entry<Long, Channel> entry : channels.entrySet()) {
            total += insertChannelIfAbsentWithDsl(tx, entry.getKey(), entry.getValue());
        }
        return total;
    }

    @Override
    public void set(long key, String name, boolean bool) {
        setWithDsl(this.dsl, key, name, bool);
    }

    /**
     * Set a boolean column using the provided DSLContext (for transactional use).
     *
     * @return number of rows updated
     */
    public int setWithDsl(DSLContext tx, long key, String name, boolean bool) {
        String columnName = SETTABLE_BOOLEAN_COLUMNS.get(name);
        if (columnName == null) {
            throw new IllegalArgumentException("Unknown or non-settable column: " + name);
        }

        return tx.update(DESTINATIONS)
                .set(field(name(columnName), Integer.class), bool ? 1 : 0)
                .where(TARGET_ID.eq(key))
                .execute();
    }

    @Override
    public void setAll(long key, Map<String, Boolean> values) {
        setAllWithDsl(this.dsl, key, values);
    }

    /**
     * Set multiple boolean columns in a single UPDATE using the provided DSLContext (for transactional use).
     *
     * @return number of rows updated
     */
    public int setAllWithDsl(DSLContext tx, long key, Map<String, Boolean> values) {
        if (values.isEmpty()) {
            return 0;
        }
        UpdateSetFirstStep<?> update = tx.update(DESTINATIONS);
        UpdateSetMoreStep<?> step = null;
        for (Map.Entry<String, Boolean> entry : values.entrySet()) {
            String columnName = SETTABLE_BOOLEAN_COLUMNS.get(entry.getKey());
            if (columnName == null) {
                throw new IllegalArgumentException("Unknown or non-settable column: " + entry.getKey());
            }
            step = (step == null ? update : step)
                    .set(field(name(columnName), Integer.class), entry.getValue() ? 1 : 0);
        }
        if (step != null) {
            return step.where(TARGET_ID.eq(key)).execute();
        }
        return 0;
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        setMinIntensityWithDsl(this.dsl, key, intensity);
    }

    /**
     * Set min intensity using the provided DSLContext (for transactional use).
     *
     * @return number of rows updated
     */
    public int setMinIntensityWithDsl(DSLContext tx, long key, SeismicIntensity intensity) {
        return tx.update(DESTINATIONS)
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
     *
     * @return number of rows updated
     */
    public int setWebhookWithDsl(DSLContext tx, long key, ChannelWebhook webhook) {
        final Long webhookId = webhook != null ? webhook.id() : null;
        return tx.update(DESTINATIONS)
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
     * @return number of rows updated
     * @throws IllegalArgumentException if lang is null
     */
    public int setLangWithDsl(DSLContext tx, long key, String lang) {
        if (lang == null) {
            throw new IllegalArgumentException("lang cannot be null");
        }
        return tx.update(DESTINATIONS)
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
                .map(url -> {
                    try {
                        return new ChannelWebhook(url).id();
                    } catch (Exception e) {
                        Log.logger.warn("Failed to parse webhook URL, skipping: {}", ChannelWebhook.maskWebhookUrl(url), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (webhookIds.isEmpty()) {
            return 0;
        }
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
        tx.select(ALL_FIELDS).from(DESTINATIONS)
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

    public DeliveryPartition getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        Condition condition = buildCondition(filter);

        Result<Record3<Long, String, String>> records = this.dsl.select(
                        TARGET_ID,
                        LANG,
                        WEBHOOK_URL
                )
                .from(DESTINATIONS)
                .where(condition)
                .fetch();

        Map<Long, DeliveryTarget> webhook = new HashMap<>();
        Map<Long, DeliveryTarget> direct = new HashMap<>();

        for (Record3<Long, String, String> r : records) {
            Long targetId = toLong(r.value1());
            if (targetId == null) continue;
            String lang = r.value2();
            String webhookUrl = r.value3();
            DeliveryTarget target = new DeliveryTarget(targetId, lang, webhookUrl);
            if (webhookUrl != null) {
                webhook.put(targetId, target);
            } else {
                direct.put(targetId, target);
            }
        }

        return new DeliveryPartition(webhook, direct);
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long targetId) {
        boolean exists = this.dsl.fetchExists(
                this.dsl.selectOne().from(DESTINATIONS)
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
        return tx.select(ALL_FIELDS).from(DESTINATIONS)
                .fetch(this::mapToDeliveryChannel);
    }

    private DeliverySnapshot.DeliveryChannel mapToDeliveryChannel(Record r) {
        ChannelWebhook webhook = null;
        String webhookUrl = r.get(WEBHOOK_URL);
        if (webhookUrl != null) {
            try {
                webhook = new ChannelWebhook(webhookUrl);
            } catch (IllegalArgumentException e) {
                Log.logger.warn("Ignoring invalid webhook URL for snapshot: {}", ChannelWebhook.maskWebhookUrl(webhookUrl), e);
            }
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
            try {
                webhook = new ChannelWebhook(webhookUrl);
            } catch (IllegalArgumentException e) {
                Log.logger.warn("Ignoring invalid webhook URL for channel: {}", ChannelWebhook.maskWebhookUrl(webhookUrl), e);
            }
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
