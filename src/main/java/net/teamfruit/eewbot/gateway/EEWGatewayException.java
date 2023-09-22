package net.teamfruit.eewbot.gateway;

public class EEWGatewayException extends Exception {

    public EEWGatewayException() {
        super();
    }

    public EEWGatewayException(String message) {
        super(message);
    }

    public EEWGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public EEWGatewayException(Throwable cause) {
        super(cause);
    }
}
