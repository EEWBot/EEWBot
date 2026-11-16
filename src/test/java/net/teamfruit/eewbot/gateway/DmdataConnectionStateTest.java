package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.gateway.DmdataConnectionState.State;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DmdataConnectionStateTest {

    @Test
    void successfulHandshakeOpensTheConnection() {
        DmdataConnectionState state = new DmdataConnectionState();
        assertThat(state.get()).isEqualTo(State.CONNECTING);
        assertThat(state.opened()).isTrue();
        assertThat(state.get()).isEqualTo(State.OPEN);
        // A late failure report cannot demote an open connection.
        assertThat(state.handshakeFailed()).isFalse();
        assertThat(state.get()).isEqualTo(State.OPEN);
    }

    @Test
    void failedHandshakeWaitsForLivenessRetry() {
        DmdataConnectionState state = new DmdataConnectionState();
        assertThat(state.handshakeFailed()).isTrue();
        assertThat(state.get()).isEqualTo(State.FAILED);
        assertThat(state.beginReconnect()).isTrue();
        assertThat(state.get()).isEqualTo(State.RECONNECTING);
    }

    @Test
    void disconnectSchedulesExactlyOneReconnect() {
        DmdataConnectionState state = new DmdataConnectionState();
        state.opened();
        assertThat(state.scheduleReconnect()).isTrue();
        assertThat(state.scheduleReconnect()).isFalse();
        assertThat(state.get()).isEqualTo(State.RECONNECT_SCHEDULED);
    }

    @Test
    void onlyOneCallerRunsAReconnect() {
        DmdataConnectionState state = new DmdataConnectionState();
        state.opened();
        state.scheduleReconnect();
        assertThat(state.beginReconnect()).isTrue();
        assertThat(state.beginReconnect()).isFalse();
        assertThat(state.scheduleReconnect()).isFalse();
    }

    @Test
    void unsuccessfulReconnectReturnsToFailedAndCanBeRetried() {
        DmdataConnectionState state = new DmdataConnectionState();
        state.opened();
        state.scheduleReconnect();
        state.beginReconnect();
        assertThat(state.reconnectFailed()).isTrue();
        assertThat(state.get()).isEqualTo(State.FAILED);
        assertThat(state.beginReconnect()).isTrue();
    }

    @Test
    void replacedIsTerminal() {
        DmdataConnectionState state = new DmdataConnectionState();
        state.opened();
        state.scheduleReconnect();
        state.beginReconnect();
        state.replaced();
        assertThat(state.reconnectFailed()).isFalse();
        assertThat(state.opened()).isFalse();
        assertThat(state.handshakeFailed()).isFalse();
        assertThat(state.scheduleReconnect()).isFalse();
        assertThat(state.beginReconnect()).isFalse();
        assertThat(state.scheduleFailed()).isFalse();
        assertThat(state.get()).isEqualTo(State.REPLACED);
    }

    @Test
    void reconnectThatCouldNotBeQueuedBecomesFailed() {
        DmdataConnectionState state = new DmdataConnectionState();
        state.opened();
        state.scheduleReconnect();
        assertThat(state.scheduleFailed()).isTrue();
        assertThat(state.get()).isEqualTo(State.FAILED);
    }
}
