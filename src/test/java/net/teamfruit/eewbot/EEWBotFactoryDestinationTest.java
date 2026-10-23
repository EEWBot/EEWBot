package net.teamfruit.eewbot;

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
    void successfulInitializationRequiresOnlyReadCapability() {
        DestinationConfigSource source = mock(DestinationConfigSource.class);
        ExecutorService reload = mock(ExecutorService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        DestinationBackend backend = new DestinationBackend() {
            @Override
            public DestinationConfigSource getConfigSource() {
                return source;
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        DeliverySnapshot initial = new DeliverySnapshot(42, List.of());
        when(source.loadSnapshot()).thenReturn(initial);

        var registries = EEWBotFactory.initializeDestinationRegistries(backend, reload, scheduler);

        assertThat(registries.backend()).isSameAs(backend);
        assertThat(registries.deliveryRegistry().getSnapshot()).isSameAs(initial);
        assertThat(closed.get()).isFalse();
        verifyNoInteractions(reload);
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(2000L), eq(2000L), any());
        registries.revisionPoller().stop();
        backend.close();
        assertThat(closed.get()).isTrue();
    }
}
