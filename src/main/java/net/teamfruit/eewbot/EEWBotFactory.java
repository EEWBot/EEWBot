package net.teamfruit.eewbot;

import com.google.gson.JsonParseException;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.channel.TextChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.thread.ThreadChannelDeleteEvent;
import discord4j.core.object.entity.User;
import discord4j.core.shard.ShardingStrategy;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.gateway.GatewayManager;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.JsonRegistry;
import net.teamfruit.eewbot.registry.config.Config;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshotLoader;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.legacy.ChannelRegistryJson;
import net.teamfruit.eewbot.registry.destination.legacy.ChannelRegistryRedis;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import net.teamfruit.eewbot.registry.destination.store.SqlAdminRegistry;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import net.teamfruit.eewbot.slashcommand.SlashCommandHandler;
import org.apache.commons.lang3.StringUtils;
import reactor.core.Disposable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class EEWBotFactory {

    public static EEWBot create() throws IOException {
        // 1. Config bootstrap
        JsonRegistry<ConfigV2> configRegistry = new JsonRegistry<>(getConfigPath(), ConfigV2::new, ConfigV2.class, Codecs.GSON_PRETTY);
        try {
            configRegistry.init(true);
        } catch (JsonParseException e) {
            JsonRegistry<Config> oldConfig = new JsonRegistry<>(getConfigPath(), Config::new, Config.class, Codecs.GSON_PRETTY);
            oldConfig.load(false);
            configRegistry.setElement(ConfigV2.fromV1(oldConfig.getElement()));
            configRegistry.save();
        }

        ConfigV2 config = configRegistry.getElement();

        migrateConfigIfNeeded(config, configRegistry);

        // 2. Environment variable overrides
        final String token = System.getenv("TOKEN");
        if (token != null)
            config.getBase().setDiscordToken(token);

        String dmdataAPIKey = System.getenv("DMDATA_API_KEY");
        if (dmdataAPIKey != null)
            config.getDmdata().setAPIKey(dmdataAPIKey);

        // 3. Validate config
        if (!config.isValid()) {
            throw new IllegalStateException("Configuration is not valid. Please set TOKEN and DMDATA_API_KEY.");
        }

        // 4. Create executors and HTTP client
        ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "eewbot-worker"));
        ScheduledExecutorService dmdataReconnectExecutor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "eewbot-dmdata-reconnect"));
        ExecutorService snapshotReloadExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "eewbot-snapshot-reload"));
        HttpClient httpClient = HttpClient.newHttpClient();

        // 5. Initialize I18n and renderer
        I18n i18n = new I18n(config.getBase().getDefaultLanguage());
        RendererQueryFactory rendererQueryFactory = new RendererQueryFactory(config.getRenderer().getAddress(), config.getRenderer().getKey());
        AtomicBoolean shutdownFlag = new AtomicBoolean(false);

        // 6. DB initialization
        ChannelRegistrySql sqlRegistry = null;
        RevisionPoller revisionPoller = null;
        DestinationDeliveryRegistry deliveryRegistry;
        DestinationAdminRegistry adminRegistry;

        try {
            Path channelsJsonPath = EEWBot.DATA_DIRECTORY != null
                    ? Paths.get(EEWBot.DATA_DIRECTORY, "channels.json")
                    : Paths.get("channels.json");
            String dbType = config.getDatabase().getType();

            switch (dbType.toLowerCase()) {
                case "postgresql" -> {
                    ChannelRegistrySql sql = ChannelRegistrySql.forPostgreSQL(config.getDatabase().getPostgresql());
                    DatabaseInitializer.migrate(sql.getDataSource(), sql.getDialect());
                    SqlRegistries result = initializeSqlRegistries(sql, snapshotReloadExecutor, scheduledExecutor);
                    sqlRegistry = sql;
                    deliveryRegistry = result.deliveryRegistry;
                    adminRegistry = result.adminRegistry;
                    revisionPoller = result.revisionPoller;
                }
                case "json" -> {
                    if (Files.notExists(channelsJsonPath)) {
                        throw new IllegalStateException(
                                "channels.json not found. New JSON deployments are not supported. "
                                        + "Please use 'sqlite' or 'postgresql' as database.type.");
                    }
                    ChannelRegistryJson registry = new ChannelRegistryJson(channelsJsonPath, Codecs.GSON);
                    registry.init(false);
                    deliveryRegistry = registry;
                    adminRegistry = registry;
                }
                case "redis" -> {
                    String redisAddress = config.getRedis().getAddress();
                    if (StringUtils.isEmpty(redisAddress)) {
                        throw new IllegalStateException(
                                "database.type is 'redis' but redis.address is not set.");
                    }
                    HostAndPort hnp = redisAddress.lastIndexOf(":") < 0
                            ? new HostAndPort(redisAddress, 6379)
                            : HostAndPort.from(redisAddress);
                    JedisPooled jedisPooled = new JedisPooled(hnp);
                    ChannelRegistryRedis registry = new ChannelRegistryRedis(jedisPooled, Codecs.GSON);
                    registry.init();
                    deliveryRegistry = registry;
                    adminRegistry = registry;
                }
                case "sqlite" -> {
                    Path sqlitePath = EEWBot.DATA_DIRECTORY != null
                            ? Paths.get(EEWBot.DATA_DIRECTORY, config.getDatabase().getSqlite().getPath())
                            : Paths.get(config.getDatabase().getSqlite().getPath());
                    ChannelRegistrySql sql = ChannelRegistrySql.forSQLite(sqlitePath);
                    DatabaseInitializer.migrate(sql.getDataSource(), sql.getDialect());
                    SqlRegistries result = initializeSqlRegistries(sql, snapshotReloadExecutor, scheduledExecutor);
                    sqlRegistry = sql;
                    deliveryRegistry = result.deliveryRegistry;
                    adminRegistry = result.adminRegistry;
                    revisionPoller = result.revisionPoller;
                }
                default -> throw new IllegalStateException(
                        "Unknown database.type: '" + dbType + "'. Supported values: sqlite, postgresql, json, redis");
            }
        } catch (Exception e) {
            scheduledExecutor.shutdown();
            dmdataReconnectExecutor.shutdown();
            snapshotReloadExecutor.shutdown();
            if (sqlRegistry != null)
                sqlRegistry.close();
            throw e;
        }

        // 7. Discord connection
        GatewayDiscordClient gateway;
        try {
            gateway = DiscordClient.create(config.getBase().getDiscordToken())
                    .gateway()
                    .setSharding(ShardingStrategy.recommended())
                    .setEnabledIntents(IntentSet.of(Intent.GUILDS))
                    .login()
                    .block();

            if (gateway == null)
                throw new RuntimeException("Discordへの接続に失敗しました。");
        } catch (Exception e) {
            if (revisionPoller != null) revisionPoller.stop();
            if (sqlRegistry != null) sqlRegistry.close();
            scheduledExecutor.shutdown();
            dmdataReconnectExecutor.shutdown();
            snapshotReloadExecutor.shutdown();
            throw e;
        }

        // 8. Bot user info
        int shardCount = gateway.getGatewayClientGroup().getShardCount();
        gateway.on(ReadyEvent.class)
                .map(event -> {
                    int count = event.getGuilds().size();
                    Log.logger.info("Connecting {} guilds...", count);
                    return count;
                })
                .take(shardCount)
                .reduce(0, Integer::sum)
                .subscribe(sum -> Log.logger.info("Connected to {} shard(s), {} guild(s)!", shardCount, sum));

        long applicationId = gateway.getSelfId().asLong();

        final User self = gateway.getSelf().block();
        String userName;
        String avatarUrl;
        if (self != null) {
            userName = self.getUsername();
            avatarUrl = self.getAvatarUrl();
            Log.logger.info("BotUser: {}", userName);
        } else {
            gateway.logout().block();
            if (revisionPoller != null) revisionPoller.stop();
            if (sqlRegistry != null) sqlRegistry.close();
            scheduledExecutor.shutdown();
            dmdataReconnectExecutor.shutdown();
            snapshotReloadExecutor.shutdown();
            throw new IllegalStateException("Failed to get bot user");
        }

        // 9. Service construction
        QuakeInfoStore quakeInfoStore = new QuakeInfoStore();
        EmbedContext embedContext = new EmbedContext(rendererQueryFactory, quakeInfoStore, i18n);
        EEWService service = new EEWService(
                gateway, deliveryRegistry, adminRegistry,
                avatarUrl, i18n, embedContext,
                scheduledExecutor, httpClient, config
        );
        ExternalWebhookService externalWebhookService = new ExternalWebhookService(config, httpClient);
        GatewayManager gatewayManager = new GatewayManager(
                service, config, applicationId, scheduledExecutor, dmdataReconnectExecutor,
                httpClient, gateway, adminRegistry, quakeInfoStore, externalWebhookService
        );

        // 10. Slash commands
        SlashCommandContext slashCtx = new SlashCommandContext(
                adminRegistry, i18n, config, gateway, httpClient,
                service, userName, avatarUrl, rendererQueryFactory,
                quakeInfoStore, gatewayManager.getTimeProvider(), applicationId, shutdownFlag
        );
        new SlashCommandHandler(slashCtx);

        // 11. Build EEWBot
        EEWBot bot = new EEWBot(
                gateway, config, deliveryRegistry, adminRegistry, sqlRegistry,
                revisionPoller, i18n, quakeInfoStore, rendererQueryFactory,
                service, gatewayManager, externalWebhookService,
                snapshotReloadExecutor, scheduledExecutor, shutdownFlag,
                applicationId, userName, avatarUrl
        );

        // 12. Event listeners (need bot instance)
        Disposable guildDeleteSub = gateway.on(GuildDeleteEvent.class)
                .subscribe(event -> bot.handleDeletion(event.getGuildId().asLong(), true));
        Disposable textChannelDeleteSub = gateway.on(TextChannelDeleteEvent.class)
                .subscribe(event -> bot.handleDeletion(event.getChannel().getId().asLong(), false));
        Disposable threadChannelDeleteSub = gateway.on(ThreadChannelDeleteEvent.class)
                .subscribe(event -> bot.handleDeletion(event.getChannel().getId().asLong(), false));
        bot.setEventSubscriptions(List.of(guildDeleteSub, textChannelDeleteSub, threadChannelDeleteSub));

        // 13. Gateway init
        gatewayManager.init();

        // 14. Shutdown hook (safety net)
        Runtime.getRuntime().addShutdownHook(new Thread(bot::close));

        return bot;
    }

    private static SqlRegistries initializeSqlRegistries(
            ChannelRegistrySql sql,
            ExecutorService snapshotReloadExecutor,
            ScheduledExecutorService scheduledExecutor
    ) {
        ConfigRevisionStore revisionStore = new ConfigRevisionStore(sql.getDsl(), sql.getDialect());
        DeliverySnapshotLoader snapshotLoader = new DeliverySnapshotLoader(sql, revisionStore);

        SnapshotDeliveryRegistry delivery = new SnapshotDeliveryRegistry(
                snapshotLoader, revisionStore, snapshotReloadExecutor
        );

        try {
            delivery.initializeSnapshot();
        } catch (Exception e) {
            Log.logger.error("Failed to initialize delivery snapshot, aborting startup", e);
            throw new RuntimeException("Snapshot initialization failed", e);
        }

        SqlAdminRegistry admin = new SqlAdminRegistry(sql, revisionStore, delivery::requestReload);

        RevisionPoller poller = new RevisionPoller(delivery, revisionStore, scheduledExecutor);
        poller.start();

        return new SqlRegistries(delivery, admin, poller);
    }

    private static void migrateConfigIfNeeded(ConfigV2 config, JsonRegistry<ConfigV2> configRegistry) throws IOException {
        if (StringUtils.isNotEmpty(config.getDatabase().getType())) return;

        Path channelsJsonPath = EEWBot.DATA_DIRECTORY != null
                ? Paths.get(EEWBot.DATA_DIRECTORY, "channels.json")
                : Paths.get("channels.json");

        if (StringUtils.isNotEmpty(config.getRedis().getAddress())) {
            config.getDatabase().setType("redis");
        } else if (Files.exists(channelsJsonPath)) {
            config.getDatabase().setType("json");
        } else {
            config.getDatabase().setType("sqlite");
        }
        configRegistry.save();
    }

    private static Path getConfigPath() {
        return EEWBot.CONFIG_DIRECTORY != null ? Paths.get(EEWBot.CONFIG_DIRECTORY, "config.json") : Paths.get("config.json");
    }

    private record SqlRegistries(
            SnapshotDeliveryRegistry deliveryRegistry,
            SqlAdminRegistry adminRegistry,
            RevisionPoller revisionPoller
    ) {
    }
}
