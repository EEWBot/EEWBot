package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.DetailQuakeInfo;
import net.teamfruit.eewbot.entity.DmdataEEW;
import net.teamfruit.eewbot.entity.KmoniEEW;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.gateway.*;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class EEWExecutor {

    private final ScheduledExecutorService executor;
    private final TimeProvider timeProvider;
    private final EEWService service;
    private final Config config;
    private final long applicationId;

    public EEWExecutor(final EEWService service, final Config config, long applicationId) {
        this.service = service;
        this.config = config;
        this.applicationId = applicationId;

        this.executor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "eewbot-communication-thread"));
        this.timeProvider = new TimeProvider(this.executor);
    }

    public ScheduledExecutorService getExecutor() {
        return this.executor;
    }

    public TimeProvider getTimeProvider() {
        return this.timeProvider;
    }

    public void init() {
        if (this.config.isEnableKyoshin()) {
            this.timeProvider.init();

            this.executor.scheduleAtFixedRate(new KmoniGateway(this.timeProvider) {

                @Override
                public void onNewData(final KmoniEEW eew) {
                    Log.logger.info(eew.toString());

                    final Predicate<Channel> isAlert = c -> eew.isAlert() ? c.eewAlert : c.eewPrediction;
                    final Predicate<Channel> decimation = c -> {
                        if (!c.eewDecimation)
                            return true;
                        if (eew.getPrev() == null)
                            return true;
                        if (eew.isInitial() || eew.isFinal())
                            return true;
                        if (eew.isAlert() != eew.getPrev().isAlert())
                            return true;
                        if (!eew.getIntensity().equals(eew.getPrev().getIntensity()))
                            return true;
                        return !eew.getRegionName().equals(eew.getPrev().getRegionName());
                    };
                    final Predicate<Channel> sensitivity = c -> {
                        if (eew.getIntensity().isEmpty())
                            return true;
                        return c.minIntensity.compareTo(eew.getIntensity().get()) <= 0;
                    };
                    EEWExecutor.this.service.sendMessage(isAlert.and(decimation).and(sensitivity), eew::createMessage);

                }
            }, 0, this.config.getKyoshinDelay(), TimeUnit.SECONDS);
        } else {
            DmdataGateway dmdataGateway = new DmdataGateway(new DmdataAPI(this.config.getDmdataAPIKey(), this.config.getDmdataOrigin()), applicationId, this.config.isDmdataMultiSocketConnect(), this.config.isDebug()) {
                @Override
                public void onNewData(DmdataEEW eew) {
                    DmdataEEW.Body currentBody = eew.getBody();
                    DmdataEEW.Body prevBody = eew.getPrev() != null ? eew.getPrev().getBody() : null;

                    boolean isWarning = currentBody.isCanceled() ? prevBody != null && prevBody.isWarning() : currentBody.isWarning();
                    Predicate<Channel> warning = c -> isWarning ? c.eewAlert : c.eewPrediction;

                    DmdataEEW.Body.Intensity currentIntensity = currentBody.getIntensity();
                    DmdataEEW.Body.Intensity prevIntensity = prevBody != null ? prevBody.getIntensity() : null;
                    boolean isImportant = prevBody == null ||
                            currentBody.isLastInfo() ||
                            currentBody.isWarning() != prevBody.isWarning() ||
                            (currentIntensity == null) != (prevIntensity == null) ||
                            currentIntensity != null && !currentIntensity.getForecastMaxInt().getFrom().equals(prevIntensity.getForecastMaxInt().getFrom()) ||
                            !currentBody.getEarthquake().getHypocenter().getName().equals(prevBody.getEarthquake().getHypocenter().getName());
                    Predicate<Channel> decimation = c -> !c.eewDecimation || isImportant;

                    SeismicIntensity maxIntensity = eew.getMaxIntensityEEW();
                    Predicate<Channel> sensitivity = c -> c.minIntensity.compareTo(maxIntensity) <= 0;
                    EEWExecutor.this.service.sendMessage(warning.and(decimation).and(sensitivity), eew::createMessage);
                }
            };
            this.executor.execute(dmdataGateway);
            this.executor.scheduleAtFixedRate(new DmdataWsLivenessChecker(dmdataGateway), 30, 30, TimeUnit.SECONDS);
        }

        this.executor.scheduleAtFixedRate(new QuakeInfoGateway() {

            @Override
            public void onNewData(final DetailQuakeInfo data) {
                Log.logger.info(data.toString());

                final Predicate<Channel> quakeInfo = c -> c.quakeInfo;
                final Predicate<Channel> sensitivity = c -> c.minIntensity.compareTo(data.getEarthquake().getIntensity()) <= 0;
                EEWExecutor.this.service.sendMessage(quakeInfo.and(sensitivity), data::createMessage);
            }
        }, 0, this.config.getQuakeInfoDelay(), TimeUnit.SECONDS);
    }

}
