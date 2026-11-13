package net.teamfruit.eewbot.registry.destination.remote;

/** Only fixed, non-confidential messages may cross the remote configuration boundary. */
public final class RemoteConfigException extends IllegalStateException {
    RemoteConfigException(String message) {
        super(message);
    }
}
