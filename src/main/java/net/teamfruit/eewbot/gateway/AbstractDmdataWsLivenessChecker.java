package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Log;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public abstract class AbstractDmdataWsLivenessChecker implements Runnable {

    protected final DmdataGateway gateway;
    protected final ExecutorService reconnectExecutor;

    private Future<?> ws1ReconnectFuture;
    private Future<?> ws2ReconnectFuture;

    protected AbstractDmdataWsLivenessChecker(DmdataGateway gateway, ExecutorService reconnectExecutor) {
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

            afterCheck();
        } finally {
            MDC.clear();
        }
    }

    /**
     * Hook invoked after the per-connection reconnect checks.
     * Subclasses may override to run additional periodic checks.
     */
    protected void afterCheck() {
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
