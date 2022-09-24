package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Log;
import org.apache.commons.lang3.exception.ExceptionUtils;

public interface Gateway<T> extends Runnable {

    void onNewData(T data);

    default void onSameData(final T data) {
    }

    default void onError(final EEWGatewayException exception) {
        Log.logger.error(ExceptionUtils.getStackTrace(exception));
    }

}
