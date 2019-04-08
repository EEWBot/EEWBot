package net.teamfruit.eewbot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import net.teamfruit.eewbot.entity.EEW;
import net.teamfruit.eewbot.entity.Monitor;
import net.teamfruit.eewbot.gateway.EEWGateway;
import net.teamfruit.eewbot.gateway.MonitorGateway;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Config;

public class EEWExecutor {

	private final ScheduledExecutorService executor;
	private final TimeProvider provider;
	private final EEWService service;
	private final Config config;

	public EEWExecutor(final EEWService service, final Config config) {
		this.service = service;
		this.config = config;

		this.executor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "EEWBot-communication-thread"));
		this.provider = new TimeProvider(this.executor);
	}

	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}

	public TimeProvider getProvider() {
		return this.provider;
	}

	public void init() {
		this.provider.init(this.config.getTimeFixDelay());

		this.executor.scheduleAtFixedRate(new EEWGateway(this.provider) {

			@Override
			public void onNewData(final EEW eew) {
				final Predicate<Channel> isAlert = c -> c.getElement(eew.isAlert() ? "eewAlert" : "eewPrediction").get();
				final Predicate<Channel> decimation = c -> {
					if (!c.getElement("eewDecimation").get())
						return true;
					if (eew.getPrev()==null)
						return true;
					if (eew.isInitial()||eew.isFinal())
						return true;
					if (eew.isAlert()!=eew.getPrev().isAlert())
						return true;
					if (!eew.getIntensity().equals(eew.getPrev().getIntensity()))
						return true;
					if (!eew.getRegionName().equals(eew.getPrev().getRegionName()))
						return true;
					return false;
				};
				EEWExecutor.this.service.sendMessage(isAlert.and(decimation), eew.createMessage());

				if (eew.isInitial()||eew.isFinal())
					EEWExecutor.this.executor.execute(new MonitorGateway(EEWExecutor.this.provider, eew) {

						@Override
						public void onNewData(final Monitor data) {
							EEWExecutor.this.service.sendMessage(c -> c.getElement("monitor").get()&&(eew.isAlert()&&c.getElement("eewAlert").get()||!eew.isAlert()&&c.getElement("eewPrediction").get()),
									data.createMessage());
						}
					});
			}
		}, 0, this.config.getKyoshinDelay(), TimeUnit.SECONDS);
	}

}
