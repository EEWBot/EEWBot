package net.teamfruit.eewbot;

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
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationBackend;
import net.teamfruit.eewbot.registry.destination.DestinationCleanup;
import net.teamfruit.eewbot.registry.destination.store.RegistryDestinationCleanup;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.store.SqlDestinationBackend;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import net.teamfruit.eewbot.slashcommand.SlashCommandHandler;
import reactor.core.Disposable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EEWBotFactory {

    public static EEWBot create() throws IOException {
        // 1. Config bootstrap
        JsonRegistry<ConfigV2> configRegistry = new JsonRegistry<>(getConfigPath(), ConfigV2::new, ConfigV2.class, Codecs.GSON_PRETTY);
        ConfigV2 config = loadConfig(configRegistry);

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
        DestinationBackend backend = null;
        RevisionPoller revisionPoller = null;
        GatewayDiscordClient gateway = null;
        EEWBot bot = null;
        GatewayManager gatewayManager = null;
        ExternalWebhookService externalWebhookService = null;
        List<Disposable> subscriptions = new ArrayList<>();
        try {
            HttpClient httpClient = HttpClient.newHttpClient();

            // 5. Initialize I18n and renderer
            I18n i18n = new I18n(config.getBase().getDefaultLanguage());
            RendererQueryFactory rendererQueryFactory = new RendererQueryFactory(config.getRenderer().getAddress(), config.getRenderer().getKey());
            AtomicBoolean shutdownFlag = new AtomicBoolean(false);

            // 6. Storage setup; the backend owns migration and SQL resource management.
            SqlDestinationBackend localBackend = SqlDestinationBackend.open(config.getDatabase(),
                    EEWBot.DATA_DIRECTORY != null ? Paths.get(EEWBot.DATA_DIRECTORY) : null);
            DestinationRegistries registries = initializeDestinationRegistries(
                    localBackend, snapshotReloadExecutor, scheduledExecutor);
            backend = registries.backend();
            SnapshotDeliveryRegistry deliveryRegistry = registries.deliveryRegistry();
            revisionPoller = registries.revisionPoller();

            // Local writes are composed separately from the read-only delivery runtime.
            DestinationAdminRegistry adminRegistry = localBackend.createAdminRegistry(deliveryRegistry::requestReload);
            DestinationCleanup destinationCleanup = new RegistryDestinationCleanup(adminRegistry);

            // 7. Discord connection
            gateway = DiscordClient.create(config.getBase().getDiscordToken())
                    .gateway()
                    .setSharding(ShardingStrategy.recommended())
                    .setEnabledIntents(IntentSet.of(Intent.GUILDS))
                    .login()
                    .block();
            if (gateway == null)
                throw new RuntimeException("Discordへの接続に失敗しました。");

            // 8. Bot user info
            int shardCount = gateway.getGatewayClientGroup().getShardCount();
            subscriptions.add(gateway.on(ReadyEvent.class)
                    .map(event -> {
                        int count = event.getGuilds().size();
                        Log.logger.info("Connecting {} guilds...", count);
                        return count;
                    })
                    .take(shardCount)
                    .reduce(0, Integer::sum)
                    .subscribe(sum -> Log.logger.info("Connected to {} shard(s), {} guild(s)!", shardCount, sum)));

            long applicationId = gateway.getSelfId().asLong();

            final User self = gateway.getSelf().block();
            String userName;
            String avatarUrl;
            if (self != null) {
                userName = self.getUsername();
                avatarUrl = self.getAvatarUrl();
                Log.logger.info("BotUser: {}", userName);
            } else {
                throw new IllegalStateException("Failed to get bot user");
            }

            // 9. Service construction
            QuakeInfoStore quakeInfoStore = new QuakeInfoStore();
            EmbedContext embedContext = new EmbedContext(rendererQueryFactory, quakeInfoStore, i18n);
            EEWService service = new EEWService(
                    gateway, deliveryRegistry, destinationCleanup,
                    avatarUrl, i18n, embedContext,
                    scheduledExecutor, httpClient, config
            );
            externalWebhookService = new ExternalWebhookService(config, httpClient);
            gatewayManager = new GatewayManager(
                    service, config, applicationId, scheduledExecutor, dmdataReconnectExecutor,
                    httpClient, quakeInfoStore, externalWebhookService
            );

            // 10. Local Interactions: one removable module, including command registration.
            SlashCommandContext slashCtx = new SlashCommandContext(
                    adminRegistry, i18n, config, gateway, httpClient,
                    userName, avatarUrl, rendererQueryFactory,
                    quakeInfoStore, applicationId, shutdownFlag
            );
            subscriptions.add(new SlashCommandHandler(slashCtx));

            // 11. Build EEWBot
            bot = new EEWBot(
                    gateway, config, deliveryRegistry, backend, destinationCleanup,
                    revisionPoller, gatewayManager, externalWebhookService,
                    snapshotReloadExecutor, scheduledExecutor, shutdownFlag
            );

            // 12. Event listeners (need bot instance)
            bot.setEventSubscriptions(subscriptions);
            EEWBot initializedBot = bot;
            subscriptions.add(gateway.on(GuildDeleteEvent.class)
                    .subscribe(initializedBot::handleGuildDeletion));
            subscriptions.add(gateway.on(TextChannelDeleteEvent.class)
                    .subscribe(event -> initializedBot.handleDeletion(event.getChannel().getId().asLong(), false)));
            subscriptions.add(gateway.on(ThreadChannelDeleteEvent.class)
                    .subscribe(event -> initializedBot.handleDeletion(event.getChannel().getId().asLong(), false)));

            // 13. Gateway init
            gatewayManager.init();

            // 14. Shutdown hook (safety net)
            Runtime.getRuntime().addShutdownHook(new Thread(initializedBot::close));

            return bot;
        } catch (IOException | RuntimeException | Error e) {
            // Every resource is owned as soon as it is acquired. A completed bot
            // uses the normal shutdown order even if listener/gateway init fails.
            if (bot != null) {
                cleanupAfterFailure(e, bot::close);
            } else {
                if (gatewayManager != null) cleanupAfterFailure(e, gatewayManager::close);
                if (revisionPoller != null) cleanupAfterFailure(e, revisionPoller::stop);
                if (gateway != null) {
                    GatewayDiscordClient connectedGateway = gateway;
                    cleanupAfterFailure(e, () -> connectedGateway.logout().block());
                }
                for (Disposable subscription : subscriptions) {
                    cleanupAfterFailure(e, subscription::dispose);
                }
                cleanupAfterFailure(e, () -> EEWBot.awaitExecutorShutdown(scheduledExecutor, 10, TimeUnit.SECONDS));
                cleanupAfterFailure(e, () -> EEWBot.awaitExecutorShutdown(snapshotReloadExecutor, 5, TimeUnit.SECONDS));
                if (backend != null) cleanupAfterFailure(e, backend::close);
                if (externalWebhookService != null) cleanupAfterFailure(e, externalWebhookService::shutdown);
            }
            cleanupAfterFailure(e, dmdataReconnectExecutor::shutdown);
            throw e;
        }
    }

    private static void cleanupAfterFailure(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    // Takes ownership of the backend; caller owns it after successful initialization.
    static DestinationRegistries initializeDestinationRegistries(
            DestinationBackend backend,
            ExecutorService snapshotReloadExecutor,
            ScheduledExecutorService scheduledExecutor
    ) {
        RevisionPoller poller = null;
        try {
            DestinationConfigSource source = backend.getConfigSource();
            SnapshotDeliveryRegistry delivery = new SnapshotDeliveryRegistry(source, snapshotReloadExecutor);
            delivery.initializeSnapshot();
            poller = new RevisionPoller(delivery, source, scheduledExecutor);
            poller.start();
            return new DestinationRegistries(backend, delivery, poller);
        } catch (RuntimeException | Error e) {
            if (poller != null) cleanupAfterFailure(e, poller::stop);
            cleanupAfterFailure(e, backend::close);
            throw e;
        }
    }

    static ConfigV2 loadConfig(JsonRegistry<ConfigV2> configRegistry) throws IOException {
        if (!configRegistry.exists()) {
            configRegistry.init(true);
            return configRegistry.getElement();
        }

        configRegistry.loadAndNormalize(true);
        return configRegistry.getElement();
    }

    private static Path getConfigPath() {
        return EEWBot.CONFIG_DIRECTORY != null ? Paths.get(EEWBot.CONFIG_DIRECTORY, "config.json") : Paths.get("config.json");
    }

    record DestinationRegistries(
            DestinationBackend backend,
            SnapshotDeliveryRegistry deliveryRegistry,
            RevisionPoller revisionPoller
    ) {
    }
}
