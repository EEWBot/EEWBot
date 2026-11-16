package net.teamfruit.eewbot.gateway;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle of one DMDATA WebSocket connection.
 * <p>
 * Every non-terminal state has an owner that is responsible for moving it forward, so a
 * connection can never be left in a state that nothing will retry. Other parties may also
 * attempt a transition (e.g. a late disconnect signal on a FAILED connection); the atomic
 * transitions ensure only one of them proceeds.
 * <ul>
 *     <li>{@link State#CONNECTING}: the handshake future (bounded by the connect timeout)</li>
 *     <li>{@link State#OPEN}: the WebSocket listener and the server-side dead check</li>
 *     <li>{@link State#RECONNECT_SCHEDULED}: the delayed reconnect task</li>
 *     <li>{@link State#RECONNECTING}: the running reconnect, which always ends in
 *     {@link State#REPLACED} or {@link State#FAILED}</li>
 *     <li>{@link State#FAILED}: the liveness checker</li>
 * </ul>
 * Transitions are atomic; each method reports whether the caller won the transition and
 * therefore owns the follow-up work.
 */
final class DmdataConnectionState {

    enum State {
        /** Handshake in flight. */
        CONNECTING,
        /** Handshake completed; telegrams are being received. */
        OPEN,
        /** Disconnected; a delayed reconnect has been queued. */
        RECONNECT_SCHEDULED,
        /** A reconnect attempt is running. */
        RECONNECTING,
        /** The handshake or a reconnect attempt failed; waiting for the liveness checker. */
        FAILED,
        /** Superseded by a newer connection. Terminal. */
        REPLACED
    }

    private static final Set<State> CAN_SCHEDULE_RECONNECT = EnumSet.of(State.CONNECTING, State.OPEN, State.FAILED);
    private static final Set<State> CAN_BEGIN_RECONNECT = EnumSet.of(State.RECONNECT_SCHEDULED, State.FAILED);

    private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTING);

    State get() {
        return this.state.get();
    }

    boolean is(State expected) {
        return this.state.get() == expected;
    }

    /** CONNECTING -> OPEN. */
    boolean opened() {
        return this.state.compareAndSet(State.CONNECTING, State.OPEN);
    }

    /** CONNECTING -> FAILED. */
    boolean handshakeFailed() {
        return this.state.compareAndSet(State.CONNECTING, State.FAILED);
    }

    /** CONNECTING | OPEN | FAILED -> RECONNECT_SCHEDULED. */
    boolean scheduleReconnect() {
        return transition(CAN_SCHEDULE_RECONNECT, State.RECONNECT_SCHEDULED);
    }

    /** RECONNECT_SCHEDULED | FAILED -> RECONNECTING. */
    boolean beginReconnect() {
        return transition(CAN_BEGIN_RECONNECT, State.RECONNECTING);
    }

    /** RECONNECTING -> FAILED. A no-op once the attempt succeeded and the connection was replaced. */
    boolean reconnectFailed() {
        return this.state.compareAndSet(State.RECONNECTING, State.FAILED);
    }

    /** RECONNECT_SCHEDULED -> FAILED, for a reconnect that could not be queued. */
    boolean scheduleFailed() {
        return this.state.compareAndSet(State.RECONNECT_SCHEDULED, State.FAILED);
    }

    /** Any state -> REPLACED. */
    void replaced() {
        this.state.set(State.REPLACED);
    }

    private boolean transition(Set<State> from, State to) {
        while (true) {
            State current = this.state.get();
            if (!from.contains(current))
                return false;
            if (this.state.compareAndSet(current, to))
                return true;
        }
    }

    @Override
    public String toString() {
        return this.state.get().name();
    }
}
