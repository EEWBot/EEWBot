package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Log;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public class DmdataWsLivenessChecker extends AbstractDmdataWsLivenessChecker {

    private int counter = 0;
    private Future<?> deadCheckFuture;

    public DmdataWsLivenessChecker(DmdataGateway gateway, ExecutorService reconnectExecutor) {
        super(gateway, reconnectExecutor);
    }

    @Override
    protected void afterCheck() {
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

        this.counter++;
    }
}
