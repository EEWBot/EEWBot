package net.teamfruit.eewbot.gateway;

import java.util.concurrent.ExecutorService;

public class DmdataWsDebugLivenessChecker extends AbstractDmdataWsLivenessChecker {

    public DmdataWsDebugLivenessChecker(DmdataGateway gateway, ExecutorService reconnectExecutor) {
        super(gateway, reconnectExecutor);
    }
}
