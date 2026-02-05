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
import java.time.OffsetDateTime;
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

            if (destination instanceof ChannelRegistrySql sqlDest) {
                String migrationName = config.sourceType + "_to_sql_channels_v1";

                if (isMigrationApplied(sqlDest, migrationName)) {
                    Log.logger.info("Migration '{}' already applied. Skipping.", migrationName);
                    return;
                }

                if (!config.dryRun) {
                    performMigrationWithTracking(source, sqlDest, migrationName, config.defaultLang);
                } else {
                    performDryRun(source);
                }
            } else {
                if (config.dryRun) {
                    performDryRun(source);
                } else {
                    performMigration(source, destination, config.defaultLang);
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

    private static void performMigrationWithTracking(ChannelRegistry source, ChannelRegistrySql dest, String migrationName, String defaultLang) {
        dest.getDsl().transaction(ctx -> {
            DSLContext tx = ctx.dsl();

            if (dest.getDialect() == org.jooq.SQLDialect.SQLITE) {
                try {
                    tx.connection(conn -> conn.createStatement().execute("BEGIN IMMEDIATE"));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to start exclusive transaction", e);
                }
            } else if (dest.getDialect() == org.jooq.SQLDialect.POSTGRES) {
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

            Log.logger.info("Performing migration: {}", migrationName);
            List<Map.Entry<Long, Channel>> entries = collectChannels(source);
            migrateChannels(entries, dest, defaultLang);

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

    private static void performMigration(ChannelRegistry source, ChannelRegistry destination, String defaultLang) {
        List<Map.Entry<Long, Channel>> entries = collectChannels(source);
        migrateChannels(entries, destination, defaultLang);
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

    // Package-private for testing
    static List<Map.Entry<Long, Channel>> collectChannels(ChannelRegistry source) {
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

    private static void migrateChannels(List<Map.Entry<Long, Channel>> entries, ChannelRegistry destination, String defaultLang) {
        if (destination instanceof ChannelRegistrySql) {
            migrateChannelsSql(entries, (ChannelRegistrySql) destination, defaultLang);
            return;
        }
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

    // Package-private for testing
    static void migrateChannelsSql(List<Map.Entry<Long, Channel>> entries, ChannelRegistrySql destination, String defaultLang) {
        DSLContext dsl = destination.getDsl();

        Table<?> destinations = table(name("destinations"));

        Field<Long> targetIdField = field(name("target_id"), Long.class);
        Field<Long> channelIdField = field(name("channel_id"), Long.class);
        Field<Long> threadIdField = field(name("thread_id"), Long.class);
        Field<Long> guildIdField = field(name("guild_id"), Long.class);
        Field<Integer> eewAlertField = field(name("eew_alert"), Integer.class);
        Field<Integer> eewPredictionField = field(name("eew_prediction"), Integer.class);
        Field<Integer> eewDecimationField = field(name("eew_decimation"), Integer.class);
        Field<Integer> quakeInfoField = field(name("quake_info"), Integer.class);
        Field<Integer> minIntensityField = field(name("min_intensity"), Integer.class);
        Field<String> langField = field(name("lang"), String.class);
        Field<String> webhookUrlField = field(name("webhook_url"), String.class);

        int count = 0;
        for (Map.Entry<Long, Channel> entry : entries) {
            Channel channel = entry.getValue();
            Long channelId = channel.getChannelId();
            Long threadId = channel.getThreadId();
            long targetId = threadId != null ? threadId : (channelId != null ? channelId : entry.getKey());
            long effectiveChannelId = channelId != null ? channelId : targetId;

            Integer minIntensity = channel.getMinIntensity() != null
                    ? channel.getMinIntensity().ordinal()
                    : SeismicIntensity.ONE.ordinal();

            // Use defaultLang if channel's lang is null
            String lang = channel.getLang() != null ? channel.getLang() : defaultLang;

            // Build webhook URL with thread_id if applicable
            String webhookUrl = null;
            if (channel.getWebhook() != null) {
                ChannelWebhook webhook = ChannelWebhook.of(
                        channel.getWebhook().id(),
                        channel.getWebhook().token(),
                        threadId
                );
                webhookUrl = webhook.getUrl();
            }

            dsl.insertInto(destinations)
                    .columns(
                            targetIdField,
                            channelIdField,
                            threadIdField,
                            guildIdField,
                            eewAlertField,
                            eewPredictionField,
                            eewDecimationField,
                            quakeInfoField,
                            minIntensityField,
                            langField,
                            webhookUrlField
                    )
                    .values(
                            targetId,
                            effectiveChannelId,
                            threadId,
                            channel.getGuildId(),
                            channel.isEewAlert() ? 1 : 0,
                            channel.isEewPrediction() ? 1 : 0,
                            channel.isEewDecimation() ? 1 : 0,
                            channel.isQuakeInfo() ? 1 : 0,
                            minIntensity,
                            lang,
                            webhookUrl
                    )
                    .onConflict(targetIdField)
                    .doUpdate()
                    .set(channelIdField, effectiveChannelId)
                    .set(threadIdField, threadId)
                    .set(guildIdField, channel.getGuildId())
                    .set(eewAlertField, channel.isEewAlert() ? 1 : 0)
                    .set(eewPredictionField, channel.isEewPrediction() ? 1 : 0)
                    .set(eewDecimationField, channel.isEewDecimation() ? 1 : 0)
                    .set(quakeInfoField, channel.isQuakeInfo() ? 1 : 0)
                    .set(minIntensityField, minIntensity)
                    .set(langField, lang)
                    .set(webhookUrlField, webhookUrl)
                    .execute();

            count++;
            if (count % 100 == 0) {
                Log.logger.info("Migrated {} / {} channels", count, entries.size());
            }
        }
        Log.logger.info("Migrated {} channels to destination", count);
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
                yield new ChannelRegistryRedis(jedisPooled, GSON);
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

    private static String nextArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            Log.logger.error("Missing value for argument: {}", flag);
            printUsage();
            System.exit(1);
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
                case "--dest-address" -> config.destConfig.put("address", nextArg(args, ++i, "--dest-address"));
                case "--dest-host" -> config.destConfig.put("host", nextArg(args, ++i, "--dest-host"));
                case "--dest-port" -> config.destConfig.put("port", nextArg(args, ++i, "--dest-port"));
                case "--dest-database" -> config.destConfig.put("database", nextArg(args, ++i, "--dest-database"));
                case "--dest-username" -> config.destConfig.put("username", nextArg(args, ++i, "--dest-username"));
                case "--dest-password" -> config.destConfig.put("password", nextArg(args, ++i, "--dest-password"));
                case "--dry-run" -> config.dryRun = true;
                case "--default-lang" -> config.defaultLang = nextArg(args, ++i, "--default-lang");
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
        System.out.println("  --default-lang <lang>     Default language for channels with null lang (default: ja_jp)");
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
        String defaultLang = "ja_jp";
    }
}
