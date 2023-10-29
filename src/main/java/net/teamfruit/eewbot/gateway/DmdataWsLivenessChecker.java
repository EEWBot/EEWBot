package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Log;

public class DmdataWsLivenessChecker implements Runnable {

    private final DmdataGateway gateway;

    public DmdataWsLivenessChecker(DmdataGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("eewbot-dmdata-liveness-chekcer-thread");

        if (this.gateway.getWebSocket1() != null)
            check(this.gateway.getWebSocket1());
        if (this.gateway.getWebSocket2() != null)
            check(this.gateway.getWebSocket2());
    }

    private void check(DmdataGateway.WebSocketListener listener) {
        if (listener.isReconnectFailed()) {
            Log.logger.warn("DMDATA WebSocket {} is not alive, reconnecting...", listener.getConnectionName());
            try {
                this.gateway.reconnectWebSocket(listener);
            } catch (EEWGatewayException e) {
                Log.logger.error("Failed to reconnect DMDATA WebSocket", e);
            }
        }
    }
}
