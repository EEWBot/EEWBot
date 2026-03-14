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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void close_releasesAllResources() throws Exception {
        when(this.mockGateway.logout()).thenReturn(Mono.empty());
        when(this.mockScheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(true);
        when(this.mockSnapshotReloadExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);

        AtomicBoolean shutdownFlag = new AtomicBoolean(false);
        EEWBot bot = createBot(this.mockSqlRegistry, this.mockRevisionPoller, this.mockExternalWebhookService, shutdownFlag);
        bot.close();

        assertTrue(shutdownFlag.get());
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
        inOrder.verify(this.mockGatewayManager).close();
        inOrder.verify(this.mockRevisionPoller).stop();
        inOrder.verify(this.mockGateway).logout();
        inOrder.verify(this.mockScheduledExecutor).shutdown();
        inOrder.verify(this.mockScheduledExecutor).awaitTermination(10, TimeUnit.SECONDS);
        inOrder.verify(this.mockSnapshotReloadExecutor).shutdown();
        inOrder.verify(this.mockSnapshotReloadExecutor).awaitTermination(5, TimeUnit.SECONDS);
        inOrder.verify(this.mockAdminRegistry).save();
        inOrder.verify(this.mockSqlRegistry).close();
        inOrder.verify(this.mockExternalWebhookService).shutdown();
    }

    @Test
    void close_handlesNullOptionalDeps() throws Exception {
        when(this.mockGateway.logout()).thenReturn(Mono.empty());
        when(this.mockScheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(true);
        when(this.mockSnapshotReloadExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);

        EEWBot bot = createBot(null, null, null, new AtomicBoolean(false));
        bot.close(); // NPE should not occur
    }

    @Test
    void close_isIdempotent() throws Exception {
        when(this.mockGateway.logout()).thenReturn(Mono.empty());
        when(this.mockScheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(true);
        when(this.mockSnapshotReloadExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);

        EEWBot bot = createBot(this.mockSqlRegistry, this.mockRevisionPoller, this.mockExternalWebhookService, new AtomicBoolean(false));
        bot.close();
        bot.close();

        verify(this.mockGatewayManager, times(1)).close();
        verify(this.mockScheduledExecutor, times(1)).shutdown();
        verify(this.mockGateway, times(1)).logout();
    }

    @Test
    void close_continuesCleanupWhenLogoutFails() throws Exception {
        when(this.mockGateway.logout()).thenReturn(Mono.error(new RuntimeException("logout failed")));
        when(this.mockScheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(true);
        when(this.mockSnapshotReloadExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);

        EEWBot bot = createBot(this.mockSqlRegistry, this.mockRevisionPoller, this.mockExternalWebhookService, new AtomicBoolean(false));

        assertDoesNotThrow(bot::close);

        verify(this.mockGatewayManager).close();
        verify(this.mockRevisionPoller).stop();
        verify(this.mockScheduledExecutor).shutdown();
        verify(this.mockScheduledExecutor).awaitTermination(10, TimeUnit.SECONDS);
        verify(this.mockSnapshotReloadExecutor).shutdown();
        verify(this.mockSnapshotReloadExecutor).awaitTermination(5, TimeUnit.SECONDS);
        verify(this.mockAdminRegistry).save();
        verify(this.mockSqlRegistry).close();
        verify(this.mockExternalWebhookService).shutdown();
    }

    @Test
    void close_forcesExecutorShutdownWhenDrainTimesOut() throws Exception {
        when(this.mockGateway.logout()).thenReturn(Mono.empty());
        when(this.mockScheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(false);
        when(this.mockSnapshotReloadExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);

        EEWBot bot = createBot(this.mockSqlRegistry, this.mockRevisionPoller, this.mockExternalWebhookService, new AtomicBoolean(false));
        bot.close();

        verify(this.mockScheduledExecutor).shutdownNow();
        verify(this.mockSnapshotReloadExecutor).shutdownNow();
    }

    @Test
    void handleDeletion_skipsRegistryMutationsAfterShutdown() {
        EEWBot bot = createBot(null, null, null, new AtomicBoolean(true));

        bot.handleDeletion(42L, false);

        verifyNoInteractions(this.mockAdminRegistry);
    }

    @Test
    void handleDeletion_removesChannelAndSavesBeforeShutdown() throws Exception {
        EEWBot bot = createBot(null, null, null, new AtomicBoolean(false));

        bot.handleDeletion(42L, false);

        InOrder inOrder = inOrder(this.mockAdminRegistry);
        inOrder.verify(this.mockAdminRegistry).remove(42L);
        inOrder.verify(this.mockAdminRegistry).save();
    }

    private EEWBot createBot(ChannelRegistrySql sqlRegistry, RevisionPoller revisionPoller, ExternalWebhookService externalWebhookService, AtomicBoolean shutdownFlag) {
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
                shutdownFlag,
                0L,   // applicationId
                null, // userName
                null  // avatarUrl
        );
    }
}
