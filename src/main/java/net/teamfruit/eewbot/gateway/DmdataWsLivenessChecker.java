package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Log;

public class DmdataWsLivenessChecker implements Runnable {

    private final DmdataGateway gateway;

    private int counter = 0;

    public DmdataWsLivenessChecker(DmdataGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("eewbot-dmdata-liveness-checker-thread");
        try {
            if (this.gateway.getWebSocket1() != null)
                check(this.gateway.getWebSocket1());
            if (this.gateway.getWebSocket2() != null)
                check(this.gateway.getWebSocket2());

            if (this.counter % 2 == 0) {
                this.gateway.reconnectDeadWebSocketsBasedOnDmData();
                this.counter = 0;
            }
        } catch (EEWGatewayException e) {
            Log.logger.error("Failed to reconnect DMDATA WebSocket", e);
        }

        this.counter++;
    }

    private void check(DmdataGateway.WebSocketListener listener) throws EEWGatewayException {
        if (listener.isReconnectFailed()) {
            Log.logger.warn("DMDATA WebSocket {} is not alive, reconnecting...", listener.getConnectionName());
            this.gateway.reconnectWebSocket(listener);
        }
    }
}
