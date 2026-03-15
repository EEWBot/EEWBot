package net.teamfruit.eewbot.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DmdataGatewayReconnectTest {

    @Mock
    private java.net.http.HttpClient httpClient;
    @Mock
    private DmdataAPI dmdataAPI;
    @Mock
    private ScheduledExecutorService reconnectScheduler;
    @Mock
    private ScheduledFuture<?> mockFuture;

    private DmdataGateway gateway;

    @BeforeEach
    void setUp() {
        this.gateway = new DmdataGateway(
                this.httpClient, this.dmdataAPI, 1L, false,
                eew -> {}, this.reconnectScheduler
        );
    }

    @Test
    void close_cancelsPendingReconnect() {
        // Create a WebSocketConnection and simulate a pending reconnect
        DmdataGateway.WebSocketConnection connection = this.gateway.new WebSocketConnection("test-1", "wss://test", true);
        connection.pendingReconnect = this.mockFuture;

        // Use reflection to set webSocket1
        try {
            var field = DmdataGateway.class.getDeclaredField("webSocket1");
            field.setAccessible(true);
            field.set(this.gateway, connection);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.gateway.close();

        verify(this.mockFuture).cancel(false);
    }

    @Test
    void onDisconnected_doesNotScheduleWhenReplaced() {
        DmdataGateway.WebSocketConnection connection = this.gateway.new WebSocketConnection("test-1", "wss://test", true);
        connection.replaced = true;

        // Get listener and call onDisconnected via onClose
        DmdataGateway.WebSocketConnection.WebSocketListener listener = connection.new WebSocketListener();

        java.net.http.WebSocket mockWs = mock(java.net.http.WebSocket.class);
        listener.onClose(mockWs, 1000, "test");

        // reconnectScheduler.schedule should never be called because replaced=true
        verify(this.reconnectScheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void onDisconnected_doesNotScheduleWhenAlreadyReconnecting() {
        DmdataGateway.WebSocketConnection connection = this.gateway.new WebSocketConnection("test-1", "wss://test", true);

        // Simulate already reconnecting
        try {
            var field = DmdataGateway.WebSocketConnection.class.getDeclaredField("reconnecting");
            field.setAccessible(true);
            field.set(connection, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        DmdataGateway.WebSocketConnection.WebSocketListener listener = connection.new WebSocketListener();

        java.net.http.WebSocket mockWs = mock(java.net.http.WebSocket.class);
        listener.onClose(mockWs, 1000, "test");

        verify(this.reconnectScheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void onDisconnected_schedulesReconnectAfterDelay() {
        when(this.reconnectScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn((ScheduledFuture) this.mockFuture);

        DmdataGateway.WebSocketConnection connection = this.gateway.new WebSocketConnection("test-1", "wss://test", true);

        // Set as webSocket1 so close() guard doesn't trigger
        try {
            var field = DmdataGateway.class.getDeclaredField("webSocket1");
            field.setAccessible(true);
            field.set(this.gateway, connection);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        DmdataGateway.WebSocketConnection.WebSocketListener listener = connection.new WebSocketListener();

        java.net.http.WebSocket mockWs = mock(java.net.http.WebSocket.class);
        listener.onClose(mockWs, 1000, "test");

        verify(this.reconnectScheduler).schedule(any(Runnable.class), eq(3L), eq(TimeUnit.SECONDS));
        assertThat((Object) connection.pendingReconnect).isEqualTo(this.mockFuture);
    }

    @Test
    void scheduledTask_skipsReconnectWhenReplacedDuringDelay() {
        // Capture the scheduled Runnable
        when(this.reconnectScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> {
                    // Don't execute the runnable yet, just return mock future
                    return this.mockFuture;
                });

        DmdataGateway.WebSocketConnection connection = this.gateway.new WebSocketConnection("test-1", "wss://test", true);

        try {
            var field = DmdataGateway.class.getDeclaredField("webSocket1");
            field.setAccessible(true);
            field.set(this.gateway, connection);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        DmdataGateway.WebSocketConnection.WebSocketListener listener = connection.new WebSocketListener();
        java.net.http.WebSocket mockWs = mock(java.net.http.WebSocket.class);
        listener.onClose(mockWs, 1000, "test");

        // Now capture and run the scheduled task after marking as replaced
        verify(this.reconnectScheduler).schedule(any(Runnable.class), eq(3L), eq(TimeUnit.SECONDS));

        // Extract the Runnable that was scheduled
        var captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(this.reconnectScheduler).schedule(captor.capture(), anyLong(), any(TimeUnit.class));
        Runnable scheduledTask = captor.getValue();

        // Mark as replaced before running
        connection.replaced = true;
        scheduledTask.run();

        // reconnecting should be reset to false
        assertThat(connection.isReconnectFailed()).isFalse();
    }
}
