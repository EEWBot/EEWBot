package net.teamfruit.eewbot;

import net.teamfruit.eewbot.registry.destination.DestinationBackend;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.delivery.SnapshotDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.remote.RemoteDestinationBackend;
import net.teamfruit.eewbot.registry.destination.remote.RemoteReadOnlyDestinationCleanup;
import net.teamfruit.eewbot.registry.destination.store.SqlDestinationBackend;
import net.teamfruit.eewbot.registry.destination.store.RegistryDestinationCleanup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.function.Supplier;
import reactor.core.Disposable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EEWBotFactoryDestinationTest {

    @TempDir Path temporary;

    @Test
    void remoteSelectionNeverOpensSqlOrTouchesLocalDatabase() throws Exception {
        ConfigV2.Database config = new ConfigV2.Database();
        config.setType("remote");
        Path db = this.temporary.resolve("channels.db");
        Files.writeString(db, "untouched database sentinel");
        var backend = mock(RemoteDestinationBackend.class);
        try (var remote = mockStatic(RemoteDestinationBackend.class); var sql = mockStatic(SqlDestinationBackend.class)) {
            remote.when(() -> RemoteDestinationBackend.open(config.getRemote())).thenReturn(backend);
            assertThat(EEWBotFactory.openDestinationBackend(config, this.temporary)).isSameAs(backend);
            sql.verifyNoInteractions();
        }
        assertThat(Files.readString(db)).isEqualTo("untouched database sentinel");
        try (var files = Files.list(this.temporary)) {
            assertThat(files.toList()).containsExactly(db);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sqlite", "postgresql"})
    void sqlSelectionPreservesManagementAndLocalInteractions(String type) throws Exception {
        ConfigV2.Database config = new ConfigV2.Database();
        config.setType(type);
        var backend = mock(SqlDestinationBackend.class);
        var admin = mock(net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry.class);
        when(backend.createAdminRegistry(any())).thenReturn(admin);
        try (var sql = mockStatic(SqlDestinationBackend.class); var remote = mockStatic(RemoteDestinationBackend.class)) {
            sql.when(() -> SqlDestinationBackend.open(config, this.temporary)).thenReturn(backend);
            assertThat(EEWBotFactory.openDestinationBackend(config, this.temporary)).isSameAs(backend);
            remote.verifyNoInteractions();
        }
        var management = EEWBotFactory.createDestinationManagement(backend, mock(SnapshotDeliveryRegistry.class));
        assertThat(management.adminRegistry()).isSameAs(admin);
        assertThat(management.cleanup()).isInstanceOf(RegistryDestinationCleanup.class);
        var handler = mock(Disposable.class);
        var subscriptions = new ArrayList<Disposable>();
        EEWBotFactory.startLocalInteractions(management, () -> handler, subscriptions);
        assertThat(subscriptions).containsExactly(handler);
    }

    @Test
    @SuppressWarnings("unchecked")
    void remoteManagementCannotConstructOrRegisterLocalInteractions() {
        var management = EEWBotFactory.createDestinationManagement(mock(RemoteDestinationBackend.class), mock(SnapshotDeliveryRegistry.class));
        assertThat(management.adminRegistry()).isNull();
        assertThat(management.cleanup()).isInstanceOf(RemoteReadOnlyDestinationCleanup.class);
        Supplier<Disposable> start = mock(Supplier.class);
        var subscriptions = new ArrayList<Disposable>();
        EEWBotFactory.startLocalInteractions(management, start, subscriptions);
        verifyNoInteractions(start);
        assertThat(subscriptions).isEmpty();
    }

    @Test
    void sqliteAndRemoteDefaultsContainNoApiOriginOrCredentials() {
        var config = new ConfigV2.Database();
        assertThat(config.getType()).isEqualTo("sqlite");
        assertThat(config.getRemote().getBaseUrl()).isEmpty();
        assertThat(config.getRemote().getPollIntervalMillis()).isEqualTo(10000);
        assertThat(Codecs.GSON.toJson(config)).doesNotContain("clientId", "clientSecret", "api-staging", "api.eewbot");
    }

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
