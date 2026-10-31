package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.SQLDataType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.jooq.impl.DSL.*;

/**
 * One-off import of the legacy SQLite/PostgreSQL destinations into the Worker's D1 through
 * {@code POST /internal/v2/migration/destinations}. The legacy store is only read; Flyway is not run on it.
 *
 * <pre>
 * java -cp eewbot.jar net.teamfruit.eewbot.registry.destination.remote.RemoteDestinationImport \
 *   --source sqlite --source-path channels.db --base-url https://api.example [--dry-run]
 * </pre>
 */
public final class RemoteDestinationImport {
    static final Set<String> LANGS = Set.of("ja_jp", "en_us", "zh_tw", "ko_hanja", "ko_kp");
    // Core's snapshot reader requires 20-256 characters; a shorter token would make the whole snapshot unloadable.
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,256}");
    private static final String WEBHOOK_PREFIX = "https://discord.com/api/webhooks/";
    private static final String LEGACY_WEBHOOK_PREFIX = "https://discordapp.com/api/webhooks/";

    private static final Field<Long> TARGET_ID = field(name("target_id"), SQLDataType.BIGINT);
    private static final Field<Long> CHANNEL_ID = field(name("channel_id"), SQLDataType.BIGINT);
    private static final Field<Long> THREAD_ID = field(name("thread_id"), SQLDataType.BIGINT);
    private static final Field<Long> GUILD_ID = field(name("guild_id"), SQLDataType.BIGINT);
    private static final Field<Long> EEW_ALERT = field(name("eew_alert"), SQLDataType.BIGINT);
    private static final Field<Long> EEW_PREDICTION = field(name("eew_prediction"), SQLDataType.BIGINT);
    private static final Field<Long> EEW_DECIMATION = field(name("eew_decimation"), SQLDataType.BIGINT);
    private static final Field<Long> QUAKE_INFO = field(name("quake_info"), SQLDataType.BIGINT);
    private static final Field<Long> TSUNAMI = field(name("tsunami"), SQLDataType.BIGINT);
    private static final Field<Long> MIN_INTENSITY = field(name("min_intensity"), SQLDataType.BIGINT);
    private static final Field<String> LANG = field(name("lang"), String.class);
    private static final Field<String> WEBHOOK_URL = field(name("webhook_url"), String.class);

    private RemoteDestinationImport() {
    }

    public static void main(String[] args) {
        int code = run(args, System::getenv, false);
        if (code != 0) System.exit(code);
    }

    /** Exit code: 0 success, 1 failure (arguments, legacy read, transport, rejection or verification mismatch). */
    static int run(String[] args, Function<String, String> env, boolean allowLoopbackHttp) {
        Options options;
        try {
            options = Options.parse(args, env);
        } catch (IllegalArgumentException e) {
            Log.logger.error("Invalid arguments: {}", e.getMessage());
            printUsage();
            return 1;
        }
        try {
            Summary summary = execute(options, env, allowLoopbackHttp);
            return summary.verifyFailures() == 0 ? 0 : 1;
        } catch (RemoteConfigException e) {
            Log.logger.error("Destination import failed: {}", e.getMessage());
            return 1;
        } catch (Exception e) {
            Log.logger.error("Destination import failed", e);
            return 1;
        }
    }

    static Summary execute(Options options, Function<String, String> env, boolean allowLoopbackHttp) throws Exception {
        Conversion conversion;
        ChannelRegistrySql legacy = openLegacy(options);
        try {
            conversion = convert(legacy.getDsl());
        } finally {
            legacy.close();
        }
        conversion.skipped().forEach((targetId, reason) ->
                Log.logger.warn("Skipping legacy destination {}: {}", targetId, reason));
        conversion.webhookDropped().forEach((targetId, reason) ->
                Log.logger.warn("Importing legacy destination {} without its webhook: {}", targetId, reason));
        Log.logger.info("Converted {} legacy destinations, skipped {}, webhook dropped {}", conversion.destinations().size(),
                conversion.skipped().size(), conversion.webhookDropped().size());

        if (options.dryRun) {
            Log.logger.info("DRY RUN: would send {} destinations in {} chunks, skipped {}, webhook dropped {}",
                    conversion.destinations().size(), chunkCount(conversion.destinations().size(), options.chunkSize),
                    conversion.skipped().size(), conversion.webhookDropped().size());
            return new Summary(0, 0, conversion.skipped().size(), conversion.webhookDropped().size(), 0);
        }

        String clientId = env.apply("EEWBOT_CF_ACCESS_CLIENT_ID");
        String clientSecret = env.apply("EEWBOT_CF_ACCESS_CLIENT_SECRET");
        int sent = 0;
        int affected = 0;
        try (RemoteMigrationApiClient client = new RemoteMigrationApiClient(options.baseUrl, clientId, clientSecret,
                options.connectTimeoutMillis, options.requestTimeoutMillis, options.maxAttempts, options.backoffMillis, allowLoopbackHttp)) {
            List<Map<String, Object>> all = conversion.destinations();
            for (int from = 0; from < all.size(); from += options.chunkSize) {
                List<Map<String, Object>> chunk = all.subList(from, Math.min(all.size(), from + options.chunkSize));
                int changed = client.importDestinations(chunk);
                sent += chunk.size();
                affected += changed;
                Log.logger.info("Imported chunk {}-{} of {} (affected {})", from + 1, from + chunk.size(), all.size(), changed);
            }
        } finally {
            Log.logger.info("Import sent {} destinations, affected {}, skipped {}, webhook dropped {}", sent, affected,
                    conversion.skipped().size(), conversion.webhookDropped().size());
        }

        int verifyFailures = 0;
        if (options.verify) {
            verifyFailures = verify(conversion.destinations(), options, clientId, clientSecret, allowLoopbackHttp);
        }
        return new Summary(sent, affected, conversion.skipped().size(), conversion.webhookDropped().size(), verifyFailures);
    }

    private static ChannelRegistrySql openLegacy(Options options) throws Exception {
        if (options.sourceType.equals("sqlite")) {
            Path path = Path.of(options.sourcePath);
            // forSQLite would otherwise create an empty file and silently import nothing.
            if (Files.notExists(path)) throw new IllegalArgumentException("SQLite file not found: " + path);
            return ChannelRegistrySql.forSQLite(path);
        }
        ConfigV2.PostgreSQL pg = new ConfigV2.PostgreSQL();
        pg.setHost(options.sourceHost);
        pg.setPort(options.sourcePort);
        pg.setDatabase(options.sourceDatabase);
        pg.setUsername(options.sourceUsername);
        pg.setPassword(options.sourcePassword);
        pg.setMaxPoolSize(2);
        pg.setMinIdle(0);
        return ChannelRegistrySql.forPostgreSQL(pg);
    }

    /**
     * Reads every legacy row in target_id order; rows the Worker would reject are skipped, never sent.
     * An unusable webhook alone does not skip the row: it is imported without one and can be restored by /setup.
     */
    static Conversion convert(DSLContext dsl) {
        List<Map<String, Object>> destinations = new ArrayList<>();
        Map<Long, String> skipped = new LinkedHashMap<>();
        Map<Long, String> webhookDropped = new LinkedHashMap<>();
        dsl.select(TARGET_ID, CHANNEL_ID, THREAD_ID, GUILD_ID, EEW_ALERT, EEW_PREDICTION, EEW_DECIMATION, QUAKE_INFO,
                        TSUNAMI, MIN_INTENSITY, LANG, WEBHOOK_URL)
                .from(table(name("destinations")))
                .orderBy(TARGET_ID)
                .fetch()
                .forEach(r -> {
                    try {
                        destinations.add(convert(r, webhookDropped));
                    } catch (Skip e) {
                        skipped.put(r.get(TARGET_ID), e.getMessage());
                    }
                });
        return new Conversion(destinations, skipped, webhookDropped);
    }

    static Map<String, Object> convert(Record r, Map<Long, String> webhookDropped) {
        long targetId = snowflake(r.get(TARGET_ID), "targetId");
        long channelId = snowflake(r.get(CHANNEL_ID), "channelId");
        Long threadId = r.get(THREAD_ID) == null ? null : snowflake(r.get(THREAD_ID), "threadId");
        Long guildId = r.get(GUILD_ID) == null ? null : snowflake(r.get(GUILD_ID), "guildId");
        if (threadId == null ? channelId != targetId : threadId != targetId || channelId == targetId) {
            throw new Skip("identity mismatch (target/channel/thread)");
        }
        Long intensity = r.get(MIN_INTENSITY);
        if (intensity == null || intensity < 0 || intensity > 9) throw new Skip("min_intensity out of range 0-9");
        String lang = r.get(LANG) == null ? null : r.get(LANG).toLowerCase(Locale.ROOT);
        if (!LANGS.contains(lang)) throw new Skip("unknown lang");

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("targetId", Long.toString(targetId));
        d.put("channelId", Long.toString(channelId));
        d.put("threadId", threadId == null ? null : Long.toString(threadId));
        d.put("guildId", guildId == null ? null : Long.toString(guildId));
        d.put("eewAlert", flag(r.get(EEW_ALERT), "eew_alert"));
        d.put("eewPrediction", flag(r.get(EEW_PREDICTION), "eew_prediction"));
        d.put("eewDecimation", flag(r.get(EEW_DECIMATION), "eew_decimation"));
        d.put("quakeInfo", flag(r.get(QUAKE_INFO), "quake_info"));
        d.put("tsunami", flag(r.get(TSUNAMI), "tsunami"));
        d.put("minIntensity", intensity.intValue());
        d.put("lang", lang);
        Map<String, Object> webhook = null;
        try {
            webhook = webhook(r.get(WEBHOOK_URL));
        } catch (Skip e) {
            webhookDropped.put(targetId, e.getMessage());
        }
        d.put("webhook", webhook);
        return d;
    }

    private static long snowflake(Long value, String field) {
        if (value == null || value <= 0) throw new Skip(field + " is not a positive Snowflake");
        return value;
    }

    private static boolean flag(Long value, String column) {
        if (value == null || (value != 0 && value != 1)) throw new Skip(column + " is not 0/1");
        return value == 1;
    }

    private static Map<String, Object> webhook(String url) {
        if (url == null) return null;
        long id;
        String token;
        // Legacy rows may still hold the old host; id and token are the same on both.
        if (url.startsWith(LEGACY_WEBHOOK_PREFIX)) url = WEBHOOK_PREFIX + url.substring(LEGACY_WEBHOOK_PREFIX.length());
        try {
            ChannelWebhook webhook = new ChannelWebhook(url);
            id = webhook.id();
            token = webhook.token();
        } catch (IllegalArgumentException e) {
            // The exception text masks the token, but nothing from the URL is logged at all.
            throw new Skip("webhook_url is not a parseable Discord webhook URL");
        }
        if (id <= 0) throw new Skip("webhook id is not a positive Snowflake");
        if (!TOKEN.matcher(token).matches()) throw new Skip("webhook token has an invalid format");
        Map<String, Object> webhook = new LinkedHashMap<>();
        webhook.put("id", Long.toString(id));
        webhook.put("token", token);
        return webhook;
    }

    /** Compares every sent destination with the Worker snapshot as Core itself parses it. Returns the mismatch count. */
    static int verify(List<Map<String, Object>> sent, Options options, String clientId, String clientSecret, boolean allowLoopbackHttp) {
        ConfigV2.Remote remote = Codecs.GSON.fromJson("{\"baseUrl\":" + Codecs.GSON.toJson(options.baseUrl)
                + ",\"connectTimeoutMillis\":" + options.connectTimeoutMillis
                + ",\"requestTimeoutMillis\":" + Math.min(60000, options.requestTimeoutMillis) + "}", ConfigV2.Remote.class);
        DeliverySnapshot snapshot;
        try (RemoteConfigApiClient client = new RemoteConfigApiClient(remote, clientId, clientSecret, allowLoopbackHttp)) {
            snapshot = new RemoteDestinationConfigSource(client).loadSnapshot();
        }
        int missing = 0;
        int different = 0;
        for (Map<String, Object> expected : sent) {
            String targetId = (String) expected.get("targetId");
            DeliverySnapshot.DeliveryChannel channel = snapshot.get(Long.parseLong(targetId));
            if (channel == null) {
                missing++;
                Log.logger.error("Verification: destination {} is missing from the remote snapshot", targetId);
                continue;
            }
            Map<String, Object> actual = toImportShape(channel);
            if (!actual.equals(expected)) {
                different++;
                Log.logger.error("Verification: destination {} differs in {}", targetId, differingFields(expected, actual));
            }
        }
        Log.logger.info("Verification at revision {}: remote {} destinations ({} not in legacy), sent {}, missing {}, different {}",
                snapshot.getRevision(), snapshot.size(), snapshot.size() - (sent.size() - missing), sent.size(), missing, different);
        return missing + different;
    }

    static Map<String, Object> toImportShape(DeliverySnapshot.DeliveryChannel c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("targetId", Long.toString(c.targetId()));
        d.put("channelId", Long.toString(c.channelId()));
        d.put("threadId", c.threadId() == null ? null : Long.toString(c.threadId()));
        d.put("guildId", c.guildId() == null ? null : Long.toString(c.guildId()));
        d.put("eewAlert", c.eewAlert());
        d.put("eewPrediction", c.eewPrediction());
        d.put("eewDecimation", c.eewDecimation());
        d.put("quakeInfo", c.quakeInfo());
        d.put("tsunami", c.tsunami());
        d.put("minIntensity", c.minIntensity().getCode());
        d.put("lang", c.lang());
        Map<String, Object> webhook = null;
        if (c.webhook() != null) {
            webhook = new LinkedHashMap<>();
            webhook.put("id", Long.toString(c.webhook().id()));
            webhook.put("token", c.webhook().token());
        }
        d.put("webhook", webhook);
        return d;
    }

    private static List<String> differingFields(Map<String, Object> expected, Map<String, Object> actual) {
        // Field names only: values include webhook tokens.
        return expected.keySet().stream().filter(k -> !Objects.equals(expected.get(k), actual.get(k))).toList();
    }

    private static int chunkCount(int size, int chunkSize) {
        return (size + chunkSize - 1) / chunkSize;
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp eewbot.jar net.teamfruit.eewbot.registry.destination.remote.RemoteDestinationImport [options]");
        System.out.println();
        System.out.println("Imports legacy destinations into the eewbot-api D1 store (upsert by targetId; never deletes).");
        System.out.println("Credentials: EEWBOT_CF_ACCESS_CLIENT_ID / EEWBOT_CF_ACCESS_CLIENT_SECRET environment variables.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --source <type>               Legacy store: sqlite (default), postgresql");
        System.out.println("  --source-path <path>          SQLite file (default: channels.db)");
        System.out.println("  --source-host <host>          PostgreSQL host (default: localhost)");
        System.out.println("  --source-port <port>          PostgreSQL port (default: 5432)");
        System.out.println("  --source-database <db>        PostgreSQL database (default: eewbot)");
        System.out.println("  --source-username <user>      PostgreSQL username");
        System.out.println("  --source-password <pass>      PostgreSQL password (or EEWBOT_LEGACY_PG_PASSWORD)");
        System.out.println("  --base-url <origin>           eewbot-api HTTPS origin (required unless --dry-run)");
        System.out.println("  --dry-run                     Convert and report skips without sending");
        System.out.println("  --no-verify                   Skip the final GET /config/snapshot comparison");
        System.out.println("  --chunk-size <n>              Destinations per request, 1-500 (default: 500)");
        System.out.println("  --max-attempts <n>            Attempts per chunk for 5xx/429/network errors (default: 8)");
        System.out.println("  --request-timeout-millis <n>  Per-request timeout (default: 30000)");
    }

    record Conversion(List<Map<String, Object>> destinations, Map<Long, String> skipped, Map<Long, String> webhookDropped) { }

    record Summary(int sent, int affected, int skipped, int webhookDropped, int verifyFailures) { }

    private static final class Skip extends RuntimeException {
        Skip(String reason) { super(reason, null, false, false); }
    }

    static final class Options {
        String sourceType = "sqlite";
        String sourcePath = "channels.db";
        String sourceHost = "localhost";
        int sourcePort = 5432;
        String sourceDatabase = "eewbot";
        String sourceUsername = "";
        String sourcePassword;
        String baseUrl;
        boolean dryRun;
        boolean verify = true;
        int chunkSize = RemoteMigrationApiClient.MAX_CHUNK;
        int maxAttempts = 8;
        long connectTimeoutMillis = 5000;
        long requestTimeoutMillis = 30000;
        long backoffMillis = 1000; // first retry delay; shortened by tests

        static Options parse(String[] args, Function<String, String> env) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--source" -> o.sourceType = value(args, ++i, arg).toLowerCase(Locale.ROOT);
                    case "--source-path" -> o.sourcePath = value(args, ++i, arg);
                    case "--source-host" -> o.sourceHost = value(args, ++i, arg);
                    case "--source-port" -> o.sourcePort = integer(args, ++i, arg, 1, 65535);
                    case "--source-database" -> o.sourceDatabase = value(args, ++i, arg);
                    case "--source-username" -> o.sourceUsername = value(args, ++i, arg);
                    case "--source-password" -> o.sourcePassword = value(args, ++i, arg);
                    case "--base-url" -> o.baseUrl = value(args, ++i, arg);
                    case "--dry-run" -> o.dryRun = true;
                    case "--no-verify" -> o.verify = false;
                    case "--chunk-size" -> o.chunkSize = integer(args, ++i, arg, 1, RemoteMigrationApiClient.MAX_CHUNK);
                    case "--max-attempts" -> o.maxAttempts = integer(args, ++i, arg, 1, 100);
                    case "--request-timeout-millis" -> o.requestTimeoutMillis = integer(args, ++i, arg, 1, 300000);
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (!o.sourceType.equals("sqlite") && !o.sourceType.equals("postgresql")) {
                throw new IllegalArgumentException("--source must be sqlite or postgresql");
            }
            if (o.sourcePassword == null) {
                String password = env.apply("EEWBOT_LEGACY_PG_PASSWORD");
                o.sourcePassword = password != null ? password : "";
            }
            if (!o.dryRun && (o.baseUrl == null || o.baseUrl.isBlank())) {
                throw new IllegalArgumentException("--base-url is required unless --dry-run");
            }
            return o;
        }

        private static String value(String[] args, int index, String flag) {
            if (index >= args.length) throw new IllegalArgumentException("Missing value for " + flag);
            return args[index];
        }

        private static int integer(String[] args, int index, String flag, int min, int max) {
            String value = value(args, index, flag);
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= min && parsed <= max) return parsed;
            } catch (NumberFormatException ignored) {
            }
            throw new IllegalArgumentException(flag + " must be an integer in " + min + "-" + max);
        }
    }
}
