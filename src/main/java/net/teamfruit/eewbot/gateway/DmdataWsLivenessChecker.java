package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Log;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public class DmdataWsLivenessChecker implements Runnable {

    private final DmdataGateway gateway;
    private final ExecutorService reconnectExecutor;

    private int counter = 0;
    private Future<?> ws1ReconnectFuture;
    private Future<?> ws2ReconnectFuture;
    private Future<?> deadCheckFuture;

    public DmdataWsLivenessChecker(DmdataGateway gateway, ExecutorService reconnectExecutor) {
        this.gateway = gateway;
        this.reconnectExecutor = reconnectExecutor;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("eewbot-dmdata-liveness-checker-thread");
        MDC.put("gateway", "dmdata");
        MDC.put("gateway.task", "liveness");
        try {
            if (this.gateway.getWebSocket1() != null)
                check(this.gateway.getWebSocket1(), false);
            if (this.gateway.getWebSocket2() != null)
                check(this.gateway.getWebSocket2(), true);

            if (this.counter % 2 == 0) {
                if (this.deadCheckFuture == null || this.deadCheckFuture.isDone()) {
                    try {
                        this.deadCheckFuture = this.reconnectExecutor.submit(() -> {
                            MDC.put("gateway", "dmdata");
                            MDC.put("gateway.task", "liveness-dead-check");
                            try {
                                this.gateway.reconnectDeadWebSocketsBasedOnDmData();
                            } catch (EEWGatewayException e) {
                                Log.logger.error("Failed to check dead DMDATA WebSockets", e);
                            } finally {
                                MDC.clear();
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        Log.logger.debug("Dead check submit skipped (executor shut down)");
                    }
                }
                this.counter = 0;
            }
        } finally {
            MDC.clear();
        }

        this.counter++;
    }

    private void check(DmdataGateway.WebSocketConnection connection, boolean isWs2) {
        if (connection.isReconnectFailed()) {
            Future<?> prev = isWs2 ? this.ws2ReconnectFuture : this.ws1ReconnectFuture;
            if (prev != null && !prev.isDone()) {
                Log.logger.debug("Reconnect already in progress for {}, skipping", connection.getConnectionName());
                return;
            }
            Log.logger.warn("DMDATA WebSocket {} is not alive, scheduling reconnect...", connection.getConnectionName());
            try {
                Future<?> future = this.reconnectExecutor.submit(() -> {
                    MDC.put("gateway", "dmdata");
                    MDC.put("gateway.task", "liveness-reconnect");
                    try {
                        this.gateway.reconnectWebSocket(connection);
                    } catch (EEWGatewayException e) {
                        Log.logger.error("Failed to reconnect DMDATA WebSocket", e);
                    } finally {
                        MDC.clear();
                    }
                });
                if (isWs2) this.ws2ReconnectFuture = future;
                else this.ws1ReconnectFuture = future;
            } catch (RejectedExecutionException e) {
                Log.logger.debug("Reconnect submit skipped (executor shut down)");
            }
        }
    }
}
