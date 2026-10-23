package net.teamfruit.eewbot;

import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationBackend;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EEWBotFactoryDestinationTest {

    @Test
    void failedInitialSnapshotClosesBackendBeforePollingStarts() {
        DestinationBackend backend = mock(DestinationBackend.class);
        DestinationConfigSource source = mock(DestinationConfigSource.class);
        ExecutorService reload = mock(ExecutorService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(backend.getConfigSource()).thenReturn(source);
        IllegalStateException failure = new IllegalStateException("snapshot unavailable");
        when(source.loadSnapshot()).thenThrow(failure);

        assertThatThrownBy(() -> EEWBotFactory.initializeDestinationRegistries(backend, reload, scheduler))
                .isSameAs(failure);

        verify(backend).close();
        verify(backend, never()).createAdminRegistry(any());
        verifyNoInteractions(scheduler, reload);
    }

    @Test
    void failedPollerStartClosesBackendAndPreservesOriginalFailure() {
        DestinationBackend backend = mock(DestinationBackend.class);
        DestinationConfigSource source = mock(DestinationConfigSource.class);
        ExecutorService reload = mock(ExecutorService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(backend.getConfigSource()).thenReturn(source);
        when(source.loadSnapshot()).thenReturn(new DeliverySnapshot(0, List.of()));
        when(backend.createAdminRegistry(any())).thenReturn(mock(DestinationAdminRegistry.class));
        IllegalStateException failure = new IllegalStateException("scheduler unavailable");
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any()))
                .thenThrow(failure);
        IllegalStateException closeFailure = new IllegalStateException("close failed");
        doThrow(closeFailure).when(backend).close();

        assertThatThrownBy(() -> EEWBotFactory.initializeDestinationRegistries(backend, reload, scheduler))
                .isSameAs(failure).hasSuppressedException(closeFailure);

        verify(backend).close();
    }

    @Test
    void successfulInitializationRetainsBackendAndWiresCommittedWriteReload() {
        DestinationBackend backend = mock(DestinationBackend.class);
        DestinationConfigSource source = mock(DestinationConfigSource.class);
        ExecutorService reload = mock(ExecutorService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(backend.getConfigSource()).thenReturn(source);
        DeliverySnapshot initial = new DeliverySnapshot(42, List.of());
        when(source.loadSnapshot()).thenReturn(initial);
        when(backend.createAdminRegistry(any())).thenAnswer(call -> {
            Runnable onWrite = call.getArgument(0);
            onWrite.run();
            return mock(DestinationAdminRegistry.class);
        });

        var registries = EEWBotFactory.initializeDestinationRegistries(backend, reload, scheduler);

        assertThat(registries.backend()).isSameAs(backend);
        assertThat(registries.deliveryRegistry().getSnapshot()).isSameAs(initial);
        verify(backend, never()).close();
        verify(reload).execute(any(Runnable.class));
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(2000L), eq(2000L), any());
        registries.revisionPoller().stop();
    }
}
