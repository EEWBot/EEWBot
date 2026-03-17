package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayManagerCloseTest {

    @Mock
    private ExecutorService mockMessageExecutor;
    @Mock
    private TimeProvider mockTimeProvider;
    @Mock
    private ScheduledExecutorService mockScheduledExecutor;
    @Mock
    private ScheduledExecutorService mockDmdataReconnectExecutor;
    @Mock
    private ScheduledFuture<?> mockFuture1;
    @Mock
    private ScheduledFuture<?> mockFuture2;

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
                null, // GatewayDiscordClient
                null, // DestinationAdminRegistry
                null, // QuakeInfoStore
                null, // ExternalWebhookService
                this.mockMessageExecutor,
                this.mockTimeProvider
        );
    }

    @Test
    void close_shutsDownMessageExecutor() {
        this.gatewayManager.close();

        verify(this.mockMessageExecutor).shutdown();
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
    void close_stopsTimeProvider() {
        this.gatewayManager.close();

        verify(this.mockTimeProvider).stop();
    }

    @Test
    void close_cancelsAllScheduledTasks() {
        // Return the same mock future for all scheduleAtFixedRate calls
        when(this.mockScheduledExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn((ScheduledFuture) this.mockFuture1);

        net.teamfruit.eewbot.registry.config.ConfigV2 config = createConfigWithLegacyQuakeInfo();
        GatewayManager manager = new GatewayManager(
                null, config, 0L,
                this.mockScheduledExecutor, this.mockDmdataReconnectExecutor,
                null, null, null, null, null,
                this.mockMessageExecutor, this.mockTimeProvider
        );

        try {
            manager.init();
        } catch (Exception ignored) {
            // Gateways may fail without real dependencies
        }

        manager.close();

        // Each scheduled task's future should be cancelled with interrupt
        verify(this.mockFuture1, atLeastOnce()).cancel(true);
    }

    private static net.teamfruit.eewbot.registry.config.ConfigV2 createMinimalConfig() {
        return new net.teamfruit.eewbot.registry.config.ConfigV2();
    }

    private static net.teamfruit.eewbot.registry.config.ConfigV2 createConfigWithLegacyQuakeInfo() {
        net.teamfruit.eewbot.registry.config.ConfigV2 config = new net.teamfruit.eewbot.registry.config.ConfigV2();
        config.getLegacy().setEnableLegacyQuakeInfo(true);
        return config;
    }
}
