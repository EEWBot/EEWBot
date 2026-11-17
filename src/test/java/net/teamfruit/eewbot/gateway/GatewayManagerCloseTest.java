package net.teamfruit.eewbot.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayManagerCloseTest {

    @Mock
    private ExecutorService mockMessageExecutor;
    @Mock
    private ScheduledExecutorService mockScheduledExecutor;
    @Mock
    private ScheduledExecutorService mockDmdataReconnectExecutor;

    private GatewayManager gatewayManager;

    @BeforeEach
    void setUp() {
        this.gatewayManager = new GatewayManager(
                null, // EEWService - not needed for close test
                createMinimalConfig(),
                0L,
                this.mockScheduledExecutor,
                this.mockDmdataReconnectExecutor,
                null, // HttpClient
                null, // QuakeInfoStore
                null, // ExternalWebhookService
                this.mockMessageExecutor
        );
    }

    @Test
    void close_shutsDownMessageExecutor() {
        this.gatewayManager.close();

        verify(this.mockMessageExecutor).shutdown();
    }

    @Test
    void jmaQueueRejectionIsPropagatedToGateway() {
        when(this.mockMessageExecutor.submit(any(Runnable.class)))
                .thenThrow(new java.util.concurrent.RejectedExecutionException("closed"));
        var report = mock(net.teamfruit.eewbot.entity.jma.AbstractJMAReport.class);
        when(report.getEventId()).thenReturn("test");
        assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> this.gatewayManager.handleJMAReport(report));
    }

    @Test
    void close_shutsDownDmdataReconnectExecutor() throws Exception {
        when(this.mockDmdataReconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);

        this.gatewayManager.close();

        verify(this.mockDmdataReconnectExecutor).shutdown();
    }

    @Test
    void close_forceShutsDmdataReconnectExecutorOnTimeout() throws Exception {
        when(this.mockDmdataReconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);

        this.gatewayManager.close();

        verify(this.mockDmdataReconnectExecutor).shutdown();
        verify(this.mockDmdataReconnectExecutor).shutdownNow();
    }

    @Test
    void close_cancelsTasksScheduledOnSharedExecutor() {
        var config = createMinimalConfig();
        config.getWebhookSender().setAddress("http://localhost:1");
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(this.mockScheduledExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn((ScheduledFuture) future);
        GatewayManager manager = new GatewayManager(
                mock(net.teamfruit.eewbot.EEWService.class), config, 0L,
                this.mockScheduledExecutor, this.mockDmdataReconnectExecutor,
                null, null, null,
                this.mockMessageExecutor
        );
        manager.init();

        manager.close();

        verify(future).cancel(true);
    }

    @Test
    void close_shutsDownOwnedPeriodicExecutors() {
        try {
            this.gatewayManager.init();
        } catch (Exception ignored) {
            // Gateways may fail without real dependencies
        }
        assertNotNull(this.gatewayManager.livenessExecutor);
        assertNotNull(this.gatewayManager.jmaXmlExecutor);

        this.gatewayManager.close();

        // shutdownNow also discards the periodic tasks scheduled on them
        assertTrue(this.gatewayManager.livenessExecutor.isShutdown());
        assertTrue(this.gatewayManager.jmaXmlExecutor.isShutdown());
    }

    private static net.teamfruit.eewbot.registry.config.ConfigV2 createMinimalConfig() {
        return new net.teamfruit.eewbot.registry.config.ConfigV2();
    }
}
