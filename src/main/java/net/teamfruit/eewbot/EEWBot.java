package net.teamfruit.eewbot;

import discord4j.core.GatewayDiscordClient;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.gateway.GatewayManager;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EEWBot implements AutoCloseable {

    public static final String DATA_DIRECTORY = System.getenv("DATA_DIRECTORY");
    public static final String CONFIG_DIRECTORY = System.getenv("CONFIG_DIRECTORY");

    private final GatewayDiscordClient gateway;
    private final ConfigV2 config;
    private final DestinationDeliveryRegistry deliveryRegistry;
    private final DestinationAdminRegistry adminRegistry;
    private final ChannelRegistrySql sqlRegistry;       // nullable (JSON/Redis backend)
    private final RevisionPoller revisionPoller;         // nullable
    private final I18n i18n;
    private final QuakeInfoStore quakeInfoStore;
    private final RendererQueryFactory rendererQueryFactory;
    private final EEWService service;
    private final GatewayManager gatewayManager;
    private final ExternalWebhookService externalWebhookService;
    private final ExecutorService snapshotReloadExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final long applicationId;
    private final String userName;
    private final String avatarUrl;

    private final AtomicBoolean closed;

    // package-private constructor (Factory only)
    EEWBot(
            GatewayDiscordClient gateway,
            ConfigV2 config,
            DestinationDeliveryRegistry deliveryRegistry,
            DestinationAdminRegistry adminRegistry,
            ChannelRegistrySql sqlRegistry,
            RevisionPoller revisionPoller,
            I18n i18n,
            QuakeInfoStore quakeInfoStore,
            RendererQueryFactory rendererQueryFactory,
            EEWService service,
            GatewayManager gatewayManager,
            ExternalWebhookService externalWebhookService,
            ExecutorService snapshotReloadExecutor,
            ScheduledExecutorService scheduledExecutor,
            AtomicBoolean closed,
            long applicationId,
            String userName,
            String avatarUrl
    ) {
        this.gateway = gateway;
        this.config = config;
        this.deliveryRegistry = deliveryRegistry;
        this.adminRegistry = adminRegistry;
        this.sqlRegistry = sqlRegistry;
        this.revisionPoller = revisionPoller;
        this.i18n = i18n;
        this.quakeInfoStore = quakeInfoStore;
        this.rendererQueryFactory = rendererQueryFactory;
        this.service = service;
        this.gatewayManager = gatewayManager;
        this.externalWebhookService = externalWebhookService;
        this.snapshotReloadExecutor = snapshotReloadExecutor;
        this.scheduledExecutor = scheduledExecutor;
        this.closed = closed;
        this.applicationId = applicationId;
        this.userName = userName;
        this.avatarUrl = avatarUrl;
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        Log.logger.info("Shutdown");
        if (this.gatewayManager != null) {
            this.gatewayManager.close();
        }
        if (this.revisionPoller != null) {
            this.revisionPoller.stop();
        }
        try {
            if (this.gateway != null) {
                this.gateway.logout().block();
            }
        } catch (final Exception e) {
            Log.logger.error("Gateway logout failed", e);
        }
        awaitExecutorShutdown(this.scheduledExecutor, 10, TimeUnit.SECONDS);
        awaitExecutorShutdown(this.snapshotReloadExecutor, 5, TimeUnit.SECONDS);
        try {
            this.adminRegistry.save();
        } catch (final IOException e) {
            Log.logger.error("Save failed", e);
        }
        if (this.sqlRegistry != null) {
            this.sqlRegistry.close();
        }
        if (this.externalWebhookService != null) {
            this.externalWebhookService.shutdown();
        }
    }

    private void awaitExecutorShutdown(ExecutorService executor, long timeout, TimeUnit unit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void awaitDisconnect() {
        this.gateway.onDisconnect().block();
    }

    // package-private: used by Factory for event listener registration
    void handleDeletion(long id, boolean isGuild) {
        if (this.closed.get()) return;
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
        return this.config;
    }

    public DestinationDeliveryRegistry getDeliveryRegistry() {
        return this.deliveryRegistry;
    }

    public DestinationAdminRegistry getAdminRegistry() {
        return this.adminRegistry;
    }

    public static void main(final String[] args) throws Exception {
        EEWBot bot = EEWBotFactory.create();
        try {
            bot.awaitDisconnect();
        } finally {
            bot.close();
        }
    }
}
