package net.teamfruit.eewbot.gateway;

import org.apache.commons.lang3.exception.ExceptionUtils;

import net.teamfruit.eewbot.Log;

public interface Gateway<T> extends Runnable {

	void onNewData(T data);

	default void onSameData(final T data) {
	}

	default void onError(final Exception exception) {
		Log.logger.error(ExceptionUtils.getStackTrace(exception));
	}

}
