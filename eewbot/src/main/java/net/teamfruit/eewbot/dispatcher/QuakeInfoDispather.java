package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jsoup.Jsoup;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.node.QuakeInfo;

public class QuakeInfoDispather implements Runnable {

	public static final String REMOTE = "https://typhoon.yahoo.co.jp/weather/earthquake/";

	private QuakeInfo prev;

	@Override
	public void run() {
		try {
			final QuakeInfo info = get(REMOTE);
			if (!info.equals(this.prev)) {
				if (this.prev!=null)
					EEWBot.instance.getClient().getDispatcher().dispatch(new QuakeInfoEvent(EEWBot.instance.getClient(), info));
				this.prev = info;
			}
		} catch (final IOException e) {
			EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static QuakeInfo get(final String remote) throws IOException {
		return new QuakeInfo(Jsoup.connect(remote).get());
	}

}
