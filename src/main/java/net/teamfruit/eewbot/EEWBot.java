package net.teamfruit.eewbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import net.teamfruit.eewbot.entity.SeismicIntensity;
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
import net.teamfruit.eewbot.registry.destination.model.*;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import net.teamfruit.eewbot.registry.destination.store.SqlAdminRegistry;
import net.teamfruit.eewbot.slashcommand.SlashCommandHandler;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class EEWBot {
    public static EEWBot instance;

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
            .registerTypeAdapter(Channel.class, new ChannelDeserializer())
            .registerTypeAdapter(ChannelWebhook.class, new ChannelWebhookDeserializer())
            .create();
    public static final Gson GSON_PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static final ObjectMapper XML_MAPPER = XmlMapper.builder().addModule(new JavaTimeModule()).build();

    public static final String DATA_DIRECTORY = System.getenv("DATA_DIRECTORY");
    public static final String CONFIG_DIRECTORY = System.getenv("CONFIG_DIRECTORY");

    private final JsonRegistry<ConfigV2> config = new JsonRegistry<>(getConfigPath(), ConfigV2::new, ConfigV2.class, GSON_PRETTY);
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "eewbot-worker"));
    private final ExecutorService snapshotReloadExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "eewbot-snapshot-reload"));
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private GatewayDiscordClient gateway;
    private DestinationDeliveryRegistry deliveryRegistry;
    private DestinationAdminRegistry adminRegistry;
    private ChannelRegistrySql sqlRegistry;
    private RevisionPoller revisionPoller;
    private I18n i18n;
    private QuakeInfoStore quakeInfoStore;
    private RendererQueryFactory rendererQueryFactory;
    private EEWService service;
    private GatewayManager gatewayManager;
    private SlashCommandHandler slashCommand;
    private ExternalWebhookService externalWebhookService;

    private long applicationId;
    private String userName;
    private String avatarUrl;

    public void initialize() throws IOException {
        try {
            this.config.init(true);
        } catch (JsonParseException e) {
            JsonRegistry<Config> oldConfig = new JsonRegistry<>(getConfigPath(), Config::new, Config.class, GSON_PRETTY);
            oldConfig.load(false);
            this.config.setElement(ConfigV2.fromV1(oldConfig.getElement()));
            this.config.save();
        }

        this.i18n = new I18n(getConfig().getBase().getDefaultLanguage());
        this.rendererQueryFactory = new RendererQueryFactory(getConfig().getRenderer().getAddress(), getConfig().getRenderer().getKey());

        migrateConfigIfNeeded();

        final String token = System.getenv("TOKEN");
        if (token != null)
            getConfig().getBase().setDiscordToken(token);

        String dmdataAPIKey = System.getenv("DMDATA_API_KEY");
        if (dmdataAPIKey != null)
            getConfig().getDmdata().setAPIKey(dmdataAPIKey);

        if (!getConfig().isValid()) {
            return;
        }

        Path channelsJsonPath = DATA_DIRECTORY != null
                ? Paths.get(DATA_DIRECTORY, "channels.json")
                : Paths.get("channels.json");
        String dbType = getConfig().getDatabase().getType();

        switch (dbType.toLowerCase()) {
            case "postgresql" -> {
                ChannelRegistrySql sql = ChannelRegistrySql.forPostgreSQL(getConfig().getDatabase().getPostgresql());
                DatabaseInitializer.migrate(sql.getDataSource(), sql.getDialect());
                initializeSqlRegistries(sql);
            }
            case "json" -> {
                if (Files.notExists(channelsJsonPath)) {
                    throw new IllegalStateException(
                            "channels.json not found. New JSON deployments are not supported. "
                                    + "Please use 'sqlite' or 'postgresql' as database.type.");
                }
                ChannelRegistryJson registry = new ChannelRegistryJson(channelsJsonPath, GSON);
                registry.init(false);
                this.deliveryRegistry = registry;
                this.adminRegistry = registry;
            }
            case "redis" -> {
                String redisAddress = getConfig().getRedis().getAddress();
                if (StringUtils.isEmpty(redisAddress)) {
                    throw new IllegalStateException(
                            "database.type is 'redis' but redis.address is not set.");
                }
                HostAndPort hnp = redisAddress.lastIndexOf(":") < 0
                        ? new HostAndPort(redisAddress, 6379)
                        : HostAndPort.from(redisAddress);
                JedisPooled jedisPooled = new JedisPooled(hnp);
                ChannelRegistryRedis registry = new ChannelRegistryRedis(jedisPooled, GSON);
                registry.init();
                this.deliveryRegistry = registry;
                this.adminRegistry = registry;
            }
            case "sqlite" -> {
                Path sqlitePath = DATA_DIRECTORY != null
                        ? Paths.get(DATA_DIRECTORY, getConfig().getDatabase().getSqlite().getPath())
                        : Paths.get(getConfig().getDatabase().getSqlite().getPath());
                ChannelRegistrySql sql = ChannelRegistrySql.forSQLite(sqlitePath);
                DatabaseInitializer.migrate(sql.getDataSource(), sql.getDialect());
                initializeSqlRegistries(sql);
            }
            default -> throw new IllegalStateException(
                    "Unknown database.type: '" + dbType + "'. Supported values: sqlite, postgresql, json, redis");
        }

        this.gateway = DiscordClient.create(getConfig().getBase().getDiscordToken())
                .gateway()
                .setSharding(ShardingStrategy.recommended())
                .setEnabledIntents(IntentSet.of(Intent.GUILDS))
                .login()
                .block();

        if (this.gateway == null)
            throw new RuntimeException("Discordへの接続に失敗しました。");

        int shardCount = this.gateway.getGatewayClientGroup().getShardCount();

        this.gateway.on(ReadyEvent.class)
                .map(event -> {
                    int count = event.getGuilds().size();
                    Log.logger.info("Connecting {} guilds...", count);
                    return count;
                })
                .take(shardCount)
                .reduce(0, Integer::sum)
                .subscribe(sum -> Log.logger.info("Connected to {} shard(s), {} guild(s)!", shardCount, sum));

        this.applicationId = this.gateway.getSelfId().asLong();

        final User self = this.gateway.getSelf().block();
        if (self != null) {
            this.userName = self.getUsername();
            this.avatarUrl = self.getAvatarUrl();

            Log.logger.info("BotUser: {}", this.userName);
        } else {
            Log.logger.error("Failed to get bot user");
            return;
        }

        this.quakeInfoStore = new QuakeInfoStore();
        EmbedContext embedContext = new EmbedContext(this.rendererQueryFactory, this.quakeInfoStore, this.i18n);
        this.service = new EEWService(
                this.gateway,
                this.deliveryRegistry,
                this.adminRegistry,
                this.avatarUrl,
                this.i18n,
                embedContext,
                this.scheduledExecutor,
                this.httpClient,
                getConfig()
        );
        this.externalWebhookService = new ExternalWebhookService(getConfig(), getHttpClient());
        this.gatewayManager = new GatewayManager(getService(), getConfig(), getApplicationId(), this.scheduledExecutor, getHttpClient(), getClient(), getAdminRegistry(), getQuakeInfoStore(), getExternalWebhookService());
        this.slashCommand = new SlashCommandHandler(this);

        this.gatewayManager.init();

        this.gateway.on(GuildDeleteEvent.class)
                .subscribe(event -> handleDeletion(event.getGuildId().asLong(), true));
        this.gateway.on(TextChannelDeleteEvent.class)
                .subscribe(event -> handleDeletion(event.getChannel().getId().asLong(), false));
        this.gateway.on(ThreadChannelDeleteEvent.class)
                .subscribe(event -> handleDeletion(event.getChannel().getId().asLong(), false));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.logger.info("Shutdown");
            if (this.gatewayManager != null) {
                this.gatewayManager.close();
            }
            if (this.revisionPoller != null) {
                this.revisionPoller.stop();
            }
            try {
                this.adminRegistry.save();
            } catch (final IOException e) {
                Log.logger.error("Save failed", e);
            }
            if (this.sqlRegistry != null) {
                this.sqlRegistry.close();
            }
            this.snapshotReloadExecutor.shutdown();
            if (this.externalWebhookService != null) {
                this.externalWebhookService.shutdown();
            }
        }));

        this.gateway.onDisconnect().block();
    }

    /**
     * Initialize SQL-backed registries with snapshot and revision poller.
     * Fail-fast: throws exception if initialization fails.
     */
    private void initializeSqlRegistries(ChannelRegistrySql sql) {
        this.sqlRegistry = sql;
        ConfigRevisionStore revisionStore = new ConfigRevisionStore(sql.getDsl(), sql.getDialect());
        DeliverySnapshotLoader snapshotLoader = new DeliverySnapshotLoader(sql, revisionStore);

        SnapshotDeliveryRegistry delivery = new SnapshotDeliveryRegistry(
                snapshotLoader,
                revisionStore,
                this.snapshotReloadExecutor
        );

        // Blocking initialization - fail fast if snapshot load fails
        try {
            delivery.initializeSnapshot();
        } catch (Exception e) {
            Log.logger.error("Failed to initialize delivery snapshot, aborting startup", e);
            throw new RuntimeException("Snapshot initialization failed", e);
        }

        SqlAdminRegistry admin = new SqlAdminRegistry(sql, revisionStore, delivery::requestReload);

        this.deliveryRegistry = delivery;
        this.adminRegistry = admin;

        // Start revision poller for external change detection
        this.revisionPoller = new RevisionPoller(delivery, revisionStore, this.scheduledExecutor);
        this.revisionPoller.start();
    }

    private void migrateConfigIfNeeded() throws IOException {
        if (StringUtils.isNotEmpty(getConfig().getDatabase().getType())) return;

        // database.type is empty: migrating from old config or new install
        Path channelsJsonPath = DATA_DIRECTORY != null
                ? Paths.get(DATA_DIRECTORY, "channels.json")
                : Paths.get("channels.json");

        if (StringUtils.isNotEmpty(getConfig().getRedis().getAddress())) {
            getConfig().getDatabase().setType("redis");
        } else if (Files.exists(channelsJsonPath)) {
            getConfig().getDatabase().setType("json");
        } else {
            getConfig().getDatabase().setType("sqlite");
        }
        this.config.save();
    }

    private void handleDeletion(long id, boolean isGuild) {
        if (isGuild)
            this.adminRegistry.removeByGuildId(id);
        else
            this.adminRegistry.remove(id);
        try {
            this.adminRegistry.save();
        } catch (IOException e) {
            Log.logger.error("Failed to save channels", e);
        }
    }

    public ConfigV2 getConfig() {
        return this.config.getElement();
    }

    public I18n getI18n() {
        return this.i18n;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return this.scheduledExecutor;
    }

    public DestinationDeliveryRegistry getDeliveryRegistry() {
        return this.deliveryRegistry;
    }

    public DestinationAdminRegistry getAdminRegistry() {
        return this.adminRegistry;
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    public GatewayDiscordClient getClient() {
        return this.gateway;
    }

    public QuakeInfoStore getQuakeInfoStore() {
        return this.quakeInfoStore;
    }

    public RendererQueryFactory getRendererQueryFactory() {
        return this.rendererQueryFactory;
    }

    public EEWService getService() {
        return this.service;
    }

    public TimeProvider getTimeProvider() {
        return this.gatewayManager.getTimeProvider();
    }

    public long getApplicationId() {
        return this.applicationId;
    }

    public String getUsername() {
        return this.userName;
    }

    public String getAvatarUrl() {
        return this.avatarUrl;
    }

    public ExternalWebhookService getExternalWebhookService() {
        return this.externalWebhookService;
    }

    private static Path getConfigPath() {
        return CONFIG_DIRECTORY != null ? Paths.get(CONFIG_DIRECTORY, "config.json") : Paths.get("config.json");
    }

    public static void main(final String[] args) throws Exception {
        instance = new EEWBot();
        instance.initialize();
    }
}
