package net.teamfruit.eewbot.slashcommand;

import discord4j.core.GatewayDiscordClient;
import net.teamfruit.eewbot.EEWService;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;

import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicBoolean;

public record SlashCommandContext(
        DestinationAdminRegistry adminRegistry,
        I18n i18n,
        ConfigV2 config,
        GatewayDiscordClient client,
        HttpClient httpClient,
        EEWService service,
        String username,
        String avatarUrl,
        RendererQueryFactory rendererQueryFactory,
        QuakeInfoStore quakeInfoStore,
        TimeProvider timeProvider,
        long applicationId,
        AtomicBoolean shutdownFlag
) {
}
