package net.teamfruit.eewbot.registry.channel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.*;

public class ChannelMigration {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
            .create();

    public static void main(String[] args) {
        try {
            MigrationConfig config = parseArguments(args);

            if (config.dryRun) {
                Log.logger.info("DRY RUN MODE - No changes will be made");
            }

            ChannelRegistry source = createRegistry(config.sourceType, config.sourceConfig);
            ChannelRegistry destination = createRegistry(config.destType, config.destConfig);

            if (destination instanceof ChannelRegistrySql) {
                ChannelRegistrySql sqlDest = (ChannelRegistrySql) destination;
                String migrationName = config.sourceType + "_to_sql_channels_v1";

                if (isMigrationApplied(sqlDest, migrationName)) {
                    Log.logger.info("Migration '{}' already applied. Skipping.", migrationName);
                    return;
                }

                if (!config.dryRun) {
                    performMigrationWithTracking(source, sqlDest, migrationName);
                } else {
                    performDryRun(source);
                }
            } else {
                if (config.dryRun) {
                    performDryRun(source);
                } else {
                    performMigration(source, destination);
                }
            }

            Log.logger.info("Migration completed successfully");

        } catch (Exception e) {
            Log.logger.error("Migration failed", e);
            System.exit(1);
        }
    }

    private static boolean isMigrationApplied(ChannelRegistrySql registry, String migrationName) {
        DSLContext dsl = registry.getDsl();
        Table<?> dataMigrations = table(name("data_migrations"));
        Field<String> nameField = field(name("name"), String.class);

        return dsl.fetchExists(
                dsl.selectFrom(dataMigrations)
                        .where(nameField.eq(migrationName))
        );
    }

    private static void performMigrationWithTracking(ChannelRegistry source, ChannelRegistrySql dest, String migrationName) {
        dest.getDsl().transaction(ctx -> {
            DSLContext tx = ctx.dsl();

            if (dest.getDialect() == org.jooq.SQLDialect.SQLITE) {
                try {
                    tx.connection(conn -> {
                        conn.createStatement().execute("BEGIN IMMEDIATE");
                    });
                } catch (Exception e) {
                    throw new RuntimeException("Failed to start exclusive transaction", e);
                }
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

            Log.logger.info("Performing migration: {}", migrationName);
            List<Map.Entry<Long, Channel>> entries = collectChannels(source);
            migrateChannels(entries, dest);

            if (dest.getDialect() == org.jooq.SQLDialect.SQLITE) {
                tx.insertInto(dataMigrations)
                        .columns(nameField, field(name("applied_at")))
                        .values(migrationName, LocalDateTime.now().toString())
                        .execute();
            } else {
                tx.insertInto(dataMigrations)
                        .columns(nameField, field(name("applied_at")))
                        .values(migrationName, LocalDateTime.now())
                        .execute();
            }

            Log.logger.info("Migration '{}' recorded", migrationName);
        });
    }

    private static void performMigration(ChannelRegistry source, ChannelRegistry destination) {
        List<Map.Entry<Long, Channel>> entries = collectChannels(source);
        migrateChannels(entries, destination);
    }

    private static void performDryRun(ChannelRegistry source) {
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

    private static List<Map.Entry<Long, Channel>> collectChannels(ChannelRegistry source) {
        List<Map.Entry<Long, Channel>> entries = new ArrayList<>();

        if (source instanceof ChannelRegistryJson) {
            ChannelRegistryJson jsonSource = (ChannelRegistryJson) source;
            entries.addAll(jsonSource.getElement().entrySet());
        } else if (source instanceof ChannelRegistryRedis) {
            Map<Long, Channel> channels = new HashMap<>();
            source.actionOnChannels(
                    ChannelFilter.builder().build(),
                    channelId -> {
                        Channel channel = source.get(channelId);
                        if (channel != null) {
                            channels.put(channelId, channel);
                        }
                    }
            );
            entries.addAll(channels.entrySet());
        } else if (source instanceof ChannelRegistrySql) {
            Map<Long, Channel> channels = new HashMap<>();
            source.actionOnChannels(
                    null,
                    channelId -> {
                        Channel channel = source.get(channelId);
                        if (channel != null) {
                            channels.put(channelId, channel);
                        }
                    }
            );
            entries.addAll(channels.entrySet());
        }

        Log.logger.info("Collected {} channels from source", entries.size());
        return entries;
    }

    private static void migrateChannels(List<Map.Entry<Long, Channel>> entries, ChannelRegistry destination) {
        int count = 0;
        for (Map.Entry<Long, Channel> entry : entries) {
            destination.computeIfAbsent(entry.getKey(), k -> entry.getValue());
            count++;
            if (count % 100 == 0) {
                Log.logger.info("Migrated {} / {} channels", count, entries.size());
            }
        }
        Log.logger.info("Migrated {} channels to destination", count);

        try {
            destination.save();
        } catch (IOException e) {
            Log.logger.error("Failed to save destination registry", e);
        }
    }

    private static ChannelRegistry createRegistry(String type, Map<String, String> config) throws IOException {
        return switch (type.toLowerCase()) {
            case "json" -> {
                String pathStr = config.getOrDefault("path", "channels.json");
                Path path = Paths.get(pathStr);
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
                ChannelRegistryRedis registry = new ChannelRegistryRedis(jedisPooled, GSON);
                yield registry;
            }
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
            default -> throw new IllegalArgumentException("Unknown registry type: " + type);
        };
    }

    private static MigrationConfig parseArguments(String[] args) {
        MigrationConfig config = new MigrationConfig();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--source" -> config.sourceType = args[++i];
                case "--dest" -> config.destType = args[++i];
                case "--source-path" -> config.sourceConfig.put("path", args[++i]);
                case "--source-address" -> config.sourceConfig.put("address", args[++i]);
                case "--source-host" -> config.sourceConfig.put("host", args[++i]);
                case "--source-port" -> config.sourceConfig.put("port", args[++i]);
                case "--source-database" -> config.sourceConfig.put("database", args[++i]);
                case "--source-username" -> config.sourceConfig.put("username", args[++i]);
                case "--source-password" -> config.sourceConfig.put("password", args[++i]);
                case "--dest-path" -> config.destConfig.put("path", args[++i]);
                case "--dest-address" -> config.destConfig.put("address", args[++i]);
                case "--dest-host" -> config.destConfig.put("host", args[++i]);
                case "--dest-port" -> config.destConfig.put("port", args[++i]);
                case "--dest-database" -> config.destConfig.put("database", args[++i]);
                case "--dest-username" -> config.destConfig.put("username", args[++i]);
                case "--dest-password" -> config.destConfig.put("password", args[++i]);
                case "--dry-run" -> config.dryRun = true;
                default -> {
                    if (arg.startsWith("--")) {
                        Log.logger.warn("Unknown argument: {}", arg);
                    }
                }
            }
        }

        if (config.sourceType == null || config.destType == null) {
            printUsage();
            System.exit(1);
        }

        return config;
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp eewbot.jar net.teamfruit.eewbot.registry.channel.ChannelMigration [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --source <type>           Source registry type: json, redis, sqlite, postgresql");
        System.out.println("  --dest <type>             Destination registry type: json, redis, sqlite, postgresql");
        System.out.println("  --source-path <path>      Source file path (for json/sqlite)");
        System.out.println("  --source-address <addr>   Source Redis address (host:port)");
        System.out.println("  --source-host <host>      Source PostgreSQL host");
        System.out.println("  --source-database <db>    Source PostgreSQL database");
        System.out.println("  --source-username <user>  Source PostgreSQL username");
        System.out.println("  --source-password <pass>  Source PostgreSQL password");
        System.out.println("  --dest-path <path>        Destination file path (for json/sqlite)");
        System.out.println("  --dest-address <addr>     Destination Redis address (host:port)");
        System.out.println("  --dest-host <host>        Destination PostgreSQL host");
        System.out.println("  --dest-database <db>      Destination PostgreSQL database");
        System.out.println("  --dest-username <user>    Destination PostgreSQL username");
        System.out.println("  --dest-password <pass>    Destination PostgreSQL password");
        System.out.println("  --dry-run                 Preview migration without making changes");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # JSON to SQLite");
        System.out.println("  java -cp eewbot.jar net.teamfruit.eewbot.registry.channel.ChannelMigration \\");
        System.out.println("    --source json --source-path channels.json \\");
        System.out.println("    --dest sqlite --dest-path channels.db");
        System.out.println();
        System.out.println("  # JSON to PostgreSQL");
        System.out.println("  java -cp eewbot.jar net.teamfruit.eewbot.registry.channel.ChannelMigration \\");
        System.out.println("    --source json --source-path channels.json \\");
        System.out.println("    --dest postgresql --dest-host localhost --dest-database eewbot");
        System.out.println();
        System.out.println("  # Redis to PostgreSQL");
        System.out.println("  java -cp eewbot.jar net.teamfruit.eewbot.registry.channel.ChannelMigration \\");
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
