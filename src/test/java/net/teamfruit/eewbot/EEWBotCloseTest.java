package net.teamfruit.eewbot;

import discord4j.core.GatewayDiscordClient;
import net.teamfruit.eewbot.gateway.GatewayManager;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.RevisionPoller;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EEWBotCloseTest {

    @Mock private GatewayDiscordClient mockGateway;
    @Mock private GatewayManager mockGatewayManager;
    @Mock private RevisionPoller mockRevisionPoller;
    @Mock private DestinationAdminRegistry mockAdminRegistry;
    @Mock private ChannelRegistrySql mockSqlRegistry;
    @Mock private ScheduledExecutorService mockScheduledExecutor;
    @Mock private ExecutorService mockSnapshotReloadExecutor;
    @Mock private ExternalWebhookService mockExternalWebhookService;

    @Test
    void close_releasesAllResources() throws IOException {
        when(this.mockGateway.logout()).thenReturn(Mono.empty());

        EEWBot bot = createBot(this.mockSqlRegistry, this.mockRevisionPoller, this.mockExternalWebhookService);
        bot.close();

        InOrder inOrder = inOrder(
                this.mockGateway,
                this.mockGatewayManager,
                this.mockRevisionPoller,
                this.mockScheduledExecutor,
                this.mockSnapshotReloadExecutor,
                this.mockAdminRegistry,
                this.mockSqlRegistry,
                this.mockExternalWebhookService
        );
        inOrder.verify(this.mockGateway).logout();
        inOrder.verify(this.mockGatewayManager).close();
        inOrder.verify(this.mockRevisionPoller).stop();
        inOrder.verify(this.mockScheduledExecutor).shutdown();
        inOrder.verify(this.mockSnapshotReloadExecutor).shutdown();
        inOrder.verify(this.mockAdminRegistry).save();
        inOrder.verify(this.mockSqlRegistry).close();
        inOrder.verify(this.mockExternalWebhookService).shutdown();
    }

    @Test
    void close_handlesNullOptionalDeps() {
        when(this.mockGateway.logout()).thenReturn(Mono.empty());

        EEWBot bot = createBot(null, null, null);
        bot.close(); // NPE should not occur
    }

    @Test
    void close_isIdempotent() {
        when(this.mockGateway.logout()).thenReturn(Mono.empty());

        EEWBot bot = createBot(this.mockSqlRegistry, this.mockRevisionPoller, this.mockExternalWebhookService);
        bot.close();
        bot.close();

        verify(this.mockGatewayManager, times(1)).close();
        verify(this.mockScheduledExecutor, times(1)).shutdown();
        verify(this.mockGateway, times(1)).logout();
    }

    @Test
    void close_continuesCleanupWhenLogoutFails() throws IOException {
        when(this.mockGateway.logout()).thenReturn(Mono.error(new RuntimeException("logout failed")));

        EEWBot bot = createBot(this.mockSqlRegistry, this.mockRevisionPoller, this.mockExternalWebhookService);

        assertDoesNotThrow(bot::close);

        verify(this.mockGatewayManager).close();
        verify(this.mockRevisionPoller).stop();
        verify(this.mockScheduledExecutor).shutdown();
        verify(this.mockSnapshotReloadExecutor).shutdown();
        verify(this.mockAdminRegistry).save();
        verify(this.mockSqlRegistry).close();
        verify(this.mockExternalWebhookService).shutdown();
    }

    private EEWBot createBot(ChannelRegistrySql sqlRegistry, RevisionPoller revisionPoller, ExternalWebhookService externalWebhookService) {
        return new EEWBot(
                this.mockGateway,
                null, // config
                null, // deliveryRegistry
                this.mockAdminRegistry,
                sqlRegistry,
                revisionPoller,
                null, // i18n
                null, // quakeInfoStore
                null, // rendererQueryFactory
                null, // service
                this.mockGatewayManager,
                externalWebhookService,
                this.mockSnapshotReloadExecutor,
                this.mockScheduledExecutor,
                0L,   // applicationId
                null, // userName
                null  // avatarUrl
        );
    }
}
