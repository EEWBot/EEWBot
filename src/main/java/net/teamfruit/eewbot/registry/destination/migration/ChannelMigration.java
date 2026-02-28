package net.teamfruit.eewbot.registry.destination.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.legacy.ChannelRegistryJson;
import net.teamfruit.eewbot.registry.destination.legacy.ChannelRegistryRedis;
import net.teamfruit.eewbot.registry.destination.model.*;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static org.jooq.impl.DSL.*;

public class ChannelMigration {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
            .registerTypeAdapter(Channel.class, new ChannelDeserializer())
            .registerTypeAdapter(ChannelWebhook.class, new ChannelWebhookDeserializer())
            .create();

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * Run the migration and return an exit code (0 = success, 1 = failure).
     * Extracted from main() for testability.
     */
    static int run(String[] args) {
        MigrationConfig config;
        try {
            config = parseArguments(args);
            validateConfig(config);
        } catch (IllegalArgumentException e) {
            Log.logger.error("Invalid arguments: {}", e.getMessage());
            printUsage();
            return 1;
        }

        DestinationAdminRegistry source = null;
        ChannelRegistrySql destination = null;
        try {
            if (config.dryRun) {
                Log.logger.info("DRY RUN MODE - No changes will be made");
            }

            source = createSourceRegistry(config.sourceType, config.sourceConfig);
            destination = createDestinationRegistry(config.destType, config.destConfig);

            String migrationName = config.sourceType + "_to_sql_channels_v1";

            if (isMigrationApplied(destination, migrationName)) {
                Log.logger.info("Migration '{}' already applied. Skipping.", migrationName);
                return 0;
            }

            if (config.dryRun) {
                performDryRun(source);
            } else {
                performMigrationWithTracking(source, destination, migrationName);
            }

            Log.logger.info("Migration completed successfully");
            return 0;

        } catch (Exception e) {
            Log.logger.error("Migration failed", e);
            return 1;
        } finally {
            closeSource(source);
            if (destination != null) {
                destination.close();
            }
        }
    }

    private static void closeSource(DestinationAdminRegistry source) {
        if (source instanceof ChannelRegistrySql sqlSource) {
            sqlSource.close();
        } else if (source instanceof ChannelRegistryRedis redisSource) {
            redisSource.close();
        }
    }

    static boolean isMigrationApplied(ChannelRegistrySql registry, String migrationName) {
        DSLContext dsl = registry.getDsl();
        Table<?> dataMigrations = table(name("data_migrations"));
        Field<String> nameField = field(name("name"), String.class);

        return dsl.fetchExists(
                dsl.selectFrom(dataMigrations)
                        .where(nameField.eq(migrationName))
        );
    }

    static void performMigrationWithTracking(DestinationAdminRegistry source, ChannelRegistrySql dest, String migrationName) {
        dest.getDsl().transaction(ctx -> {
            DSLContext tx = ctx.dsl();

            if (dest.getDialect() == org.jooq.SQLDialect.POSTGRES) {
                tx.fetch("SELECT pg_advisory_xact_lock(?)", 0x454557424F54L);
            }

            Table<?> dataMigrations = table(name("data_migrations"));
            Field<String> nameField = field(name("name"), String.class);

            boolean applied = tx.fetchExists(
                    tx.selectFrom(dataMigrations)
                            .where(nameField.eq(migrationName))
            );

            if (applied) {
                Log.logger.info("Migration '{}' already applied in transaction, skipping", migrationName);
                return;
            }

            boolean hasExistingData = tx.fetchExists(tx.selectOne().from(table(name("destinations"))));
            if (hasExistingData) {
                throw new IllegalStateException(
                        "Destination database already contains data in 'destinations' table. "
                                + "Migration requires an empty destination. Clear the table first or use a fresh database.");
            }

            Log.logger.info("Performing migration: {}", migrationName);
            List<Map.Entry<Long, Channel>> entries = collectChannels(source);
            migrateChannelsSql(entries, dest, tx);
            new ConfigRevisionStore(tx, dest.getDialect()).incrementWithDsl(tx);

            if (dest.getDialect() == org.jooq.SQLDialect.SQLITE) {
                tx.insertInto(dataMigrations)
                        .columns(nameField, field(name("applied_at")), field(name("checksum")), field(name("meta")))
                        .values(migrationName, LocalDateTime.now().toString(), "", null)
                        .execute();
            } else {
                tx.insertInto(dataMigrations)
                        .columns(nameField, field(name("applied_at")), field(name("checksum")), field(name("meta")))
                        .values(migrationName, OffsetDateTime.now(), "", null)
                        .execute();
            }

            Log.logger.info("Migration '{}' recorded", migrationName);
        });
    }

    private static void performDryRun(DestinationAdminRegistry source) {
        List<Map.Entry<Long, Channel>> entries = collectChannels(source);
        Log.logger.info("DRY RUN: Would migrate {} channels", entries.size());
        for (Map.Entry<Long, Channel> entry : entries) {
            Log.logger.info("  Channel {}: eewAlert={}, eewPrediction={}, quakeInfo={}, webhook={}",
                    entry.getKey(),
                    entry.getValue().isEewAlert(),
                    entry.getValue().isEewPrediction(),
                    entry.getValue().isQuakeInfo(),
                    entry.getValue().getWebhook() != null ? "present" : "absent");
        }
    }

    public static List<Map.Entry<Long, Channel>> collectChannels(DestinationAdminRegistry source) {
        List<Map.Entry<Long, Channel>> entries = new ArrayList<>();

        if (source instanceof ChannelRegistryJson jsonSource) {
            entries.addAll(jsonSource.getElement().entrySet());
        } else {
            // Use the optimized getAllChannels method for all other registries
            Map<Long, Channel> channels = source.getAllChannels();
            entries.addAll(channels.entrySet());
        }

        Log.logger.info("Collected {} channels from source", entries.size());
        return entries;
    }

    public static void migrateChannelsSql(List<Map.Entry<Long, Channel>> entries, ChannelRegistrySql destination, DSLContext tx) {
        Map<Long, Channel> prepared = new LinkedHashMap<>(entries.size());
        for (Map.Entry<Long, Channel> entry : entries) {
            Channel channel = entry.getValue();
            Long channelId = channel.getChannelId();
            Long threadId = channel.getThreadId();
            long targetId = threadId != null ? threadId : (channelId != null ? channelId : entry.getKey());
            long effectiveChannelId = channelId != null ? channelId : targetId;

            ChannelWebhook webhook = null;
            if (channel.getWebhook() != null) {
                webhook = ChannelWebhook.of(
                        channel.getWebhook().id(),
                        channel.getWebhook().token(),
                        threadId
                );
            }

            Channel preparedChannel = new Channel(
                    channel.getGuildId(), effectiveChannelId, threadId,
                    channel.isEewAlert(), channel.isEewPrediction(), channel.isEewDecimation(), channel.isQuakeInfo(),
                    channel.isTsunami(), channel.getMinIntensity(), webhook, channel.getLang()
            );
            prepared.put(targetId, preparedChannel);
        }

        int insertedCount = destination.putAllWithDsl(tx, prepared);
        Log.logger.info("Migrated {}/{} channels to destination", insertedCount, prepared.size());
        if (insertedCount != prepared.size()) {
            throw new IllegalStateException(
                    "Expected to insert " + prepared.size() + " channels but only " + insertedCount
                            + " were inserted. This may indicate duplicate target IDs in the source data.");
        }
    }

    private static DestinationAdminRegistry createSourceRegistry(String type, Map<String, String> config) throws IOException {
        return switch (type.toLowerCase()) {
            case "json" -> {
                String pathStr = config.getOrDefault("path", "channels.json");
                Path path = Paths.get(pathStr);
                if (Files.notExists(path)) {
                    throw new IllegalArgumentException(
                            "JSON file not found: " + path + ". JSON can only be used as a source with an existing file.");
                }
                ChannelRegistryJson registry = new ChannelRegistryJson(path, GSON);
                registry.init(false);
                yield registry;
            }
            case "redis" -> {
                String address = config.getOrDefault("address", "localhost:6379");
                HostAndPort hnp = address.lastIndexOf(":") < 0
                        ? new HostAndPort(address, 6379)
                        : HostAndPort.from(address);
                JedisPooled jedisPooled = new JedisPooled(hnp);
                yield new ChannelRegistryRedis(jedisPooled, GSON);
            }
            case "sqlite" -> {
                String pathStr = config.getOrDefault("path", "channels.db");
                Path path = Paths.get(pathStr);
                if (Files.notExists(path)) {
                    throw new IllegalArgumentException(
                            "SQLite file not found: " + path + ". SQLite can only be used as a source with an existing database file.");
                }
                yield createSqlRegistry(type, config);
            }
            case "postgresql" -> createSqlRegistry(type, config);
            default -> throw new IllegalArgumentException("Unknown source registry type: " + type);
        };
    }

    private static ChannelRegistrySql createSqlRegistry(String type, Map<String, String> config) throws IOException {
        return switch (type.toLowerCase()) {
            case "sqlite" -> {
                String pathStr = config.getOrDefault("path", "channels.db");
                Path path = Paths.get(pathStr);
                ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(path);
                DatabaseInitializer.migrate(registry.getDataSource(), registry.getDialect());
                yield registry;
            }
            case "postgresql" -> {
                ConfigV2.PostgreSQL pgConfig = new ConfigV2.PostgreSQL();
                pgConfig.setHost(config.getOrDefault("host", "localhost"));
                pgConfig.setPort(Integer.parseInt(config.getOrDefault("port", "5432")));
                pgConfig.setDatabase(config.getOrDefault("database", "eewbot"));
                pgConfig.setUsername(config.getOrDefault("username", ""));
                pgConfig.setPassword(config.getOrDefault("password", ""));
                pgConfig.setMaxPoolSize(Integer.parseInt(config.getOrDefault("max-pool-size", "10")));
                pgConfig.setMinIdle(Integer.parseInt(config.getOrDefault("min-idle", "2")));
                ChannelRegistrySql registry = ChannelRegistrySql.forPostgreSQL(pgConfig);
                DatabaseInitializer.migrate(registry.getDataSource(), registry.getDialect());
                yield registry;
            }
            default -> throw new IllegalArgumentException("Unknown SQL registry type: " + type);
        };
    }

    private static ChannelRegistrySql createDestinationRegistry(String type, Map<String, String> config) throws IOException {
        return switch (type.toLowerCase()) {
            case "sqlite", "postgresql" -> createSqlRegistry(type, config);
            default -> throw new IllegalArgumentException("Unsupported destination type: " + type + ". Destination must be sqlite or postgresql.");
        };
    }

    private static String nextArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for argument: " + flag);
        }
        return args[index];
    }

    private static MigrationConfig parseArguments(String[] args) {
        MigrationConfig config = new MigrationConfig();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--source" -> config.sourceType = nextArg(args, ++i, "--source");
                case "--dest" -> config.destType = nextArg(args, ++i, "--dest");
                case "--source-path" -> config.sourceConfig.put("path", nextArg(args, ++i, "--source-path"));
                case "--source-address" -> config.sourceConfig.put("address", nextArg(args, ++i, "--source-address"));
                case "--source-host" -> config.sourceConfig.put("host", nextArg(args, ++i, "--source-host"));
                case "--source-port" -> config.sourceConfig.put("port", nextArg(args, ++i, "--source-port"));
                case "--source-database" -> config.sourceConfig.put("database", nextArg(args, ++i, "--source-database"));
                case "--source-username" -> config.sourceConfig.put("username", nextArg(args, ++i, "--source-username"));
                case "--source-password" -> config.sourceConfig.put("password", nextArg(args, ++i, "--source-password"));
                case "--dest-path" -> config.destConfig.put("path", nextArg(args, ++i, "--dest-path"));
                case "--dest-host" -> config.destConfig.put("host", nextArg(args, ++i, "--dest-host"));
                case "--dest-port" -> config.destConfig.put("port", nextArg(args, ++i, "--dest-port"));
                case "--dest-database" -> config.destConfig.put("database", nextArg(args, ++i, "--dest-database"));
                case "--dest-username" -> config.destConfig.put("username", nextArg(args, ++i, "--dest-username"));
                case "--dest-password" -> config.destConfig.put("password", nextArg(args, ++i, "--dest-password"));
                case "--dry-run" -> config.dryRun = true;
                default -> {
                    if (arg.startsWith("--")) {
                        Log.logger.warn("Unknown argument: {}", arg);
                    }
                }
            }
        }

        if (config.sourceType == null || config.destType == null) {
            throw new IllegalArgumentException("Both --source and --dest are required. " +
                    "Usage: --source <type> --dest <type> [options]");
        }

        return config;
    }

    private static void validateConfig(MigrationConfig config) {
        Set<String> validSourceTypes = Set.of("json", "redis", "sqlite", "postgresql");
        Set<String> validDestTypes = Set.of("sqlite", "postgresql");

        if (!validSourceTypes.contains(config.sourceType.toLowerCase())) {
            throw new IllegalArgumentException("Unknown source type: " + config.sourceType
                    + ". Must be one of: " + validSourceTypes);
        }
        if (!validDestTypes.contains(config.destType.toLowerCase())) {
            throw new IllegalArgumentException("Unknown destination type: " + config.destType
                    + ". Must be one of: " + validDestTypes);
        }

        validatePort(config.sourceConfig, "--source-port");
        validatePort(config.destConfig, "--dest-port");
    }

    private static void validatePort(Map<String, String> config, String flag) {
        String port = config.get("port");
        if (port != null) {
            try {
                Integer.parseInt(port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid value for " + flag + ": " + port + " (must be an integer)");
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp eewbot.jar net.teamfruit.eewbot.registry.destination.migration.ChannelMigration [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --source <type>           Source registry type: json, redis, sqlite, postgresql");
        System.out.println("  --dest <type>             Destination registry type: sqlite, postgresql");
        System.out.println("  --source-path <path>      Source file path (for json/sqlite)");
        System.out.println("  --source-address <addr>   Source Redis address (host:port)");
        System.out.println("  --source-host <host>      Source PostgreSQL host");
        System.out.println("  --source-port <port>      Source PostgreSQL port");
        System.out.println("  --source-database <db>    Source PostgreSQL database");
        System.out.println("  --source-username <user>  Source PostgreSQL username");
        System.out.println("  --source-password <pass>  Source PostgreSQL password");
        System.out.println("  --dest-path <path>        Destination file path (for sqlite)");
        System.out.println("  --dest-host <host>        Destination PostgreSQL host");
        System.out.println("  --dest-port <port>        Destination PostgreSQL port");
        System.out.println("  --dest-database <db>      Destination PostgreSQL database");
        System.out.println("  --dest-username <user>    Destination PostgreSQL username");
        System.out.println("  --dest-password <pass>    Destination PostgreSQL password");
        System.out.println("  --dry-run                 Preview migration without making changes");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # JSON to SQLite");
        System.out.println("  java -cp eewbot.jar net.teamfruit.eewbot.registry.destination.migration.ChannelMigration \\");
        System.out.println("    --source json --source-path channels.json \\");
        System.out.println("    --dest sqlite --dest-path channels.db");
        System.out.println();
        System.out.println("  # JSON to PostgreSQL");
        System.out.println("  java -cp eewbot.jar net.teamfruit.eewbot.registry.destination.migration.ChannelMigration \\");
        System.out.println("    --source json --source-path channels.json \\");
        System.out.println("    --dest postgresql --dest-host localhost --dest-database eewbot");
        System.out.println();
        System.out.println("  # Redis to PostgreSQL");
        System.out.println("  java -cp eewbot.jar net.teamfruit.eewbot.registry.destination.migration.ChannelMigration \\");
        System.out.println("    --source redis --source-address localhost:6379 \\");
        System.out.println("    --dest postgresql --dest-host localhost --dest-database eewbot");
    }

    private static class MigrationConfig {
        String sourceType;
        String destType;
        Map<String, String> sourceConfig = new HashMap<>();
        Map<String, String> destConfig = new HashMap<>();
        boolean dryRun = false;
    }
}
