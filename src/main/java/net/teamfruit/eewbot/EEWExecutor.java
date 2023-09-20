package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.DetailQuakeInfo;
import net.teamfruit.eewbot.entity.DmdataEEW;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.gateway.DmdataGateway;
import net.teamfruit.eewbot.gateway.DmdataWsLivenessChecker;
import net.teamfruit.eewbot.gateway.QuakeInfoGateway;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Config;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class EEWExecutor {

    private final ScheduledExecutorService executor;
    private final TimeProvider provider;
    private final EEWService service;
    private final Config config;
    private final long applicationId;

    public EEWExecutor(final EEWService service, final Config config, long applicationId) {
        this.service = service;
        this.config = config;
        this.applicationId = applicationId;

        this.executor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "eewbot-communication-thread"));
        this.provider = new TimeProvider(this.executor);
    }

    public ScheduledExecutorService getExecutor() {
        return this.executor;
    }

    public TimeProvider getProvider() {
        return this.provider;
    }

    public void init() {
        this.provider.init();

        DmdataGateway dmdataGateway = new DmdataGateway(this.config.getDmdataAPIKey(), this.config.getDmdataOrigin(), applicationId, this.config.isDmdataMultiSocketConnect(), this.config.isDebug()) {
            @Override
            public void onNewData(DmdataEEW eew) {
                Predicate<Channel> isAlert = c -> eew.getBody().isWarning() ? c.eewAlert : c.eewPrediction;
                Predicate<Channel> decimation = c -> {
                    if (!c.eewDecimation)
                        return true;
                    if (eew.getPrev() == null)
                        return true;
                    if (eew.getSerialNo().equals("1") || eew.getBody().isLastInfo())
                        return true;
                    if (eew.getBody().isWarning() != eew.getPrev().getBody().isWarning())
                        return true;
                    if (!eew.getBody().getIntensity().getForecastMaxInt().getFrom().equals(eew.getPrev().getBody().getIntensity().getForecastMaxInt().getFrom()))
                        return true;
                    return !eew.getBody().getEarthquake().getHypocenter().getName().equals(eew.getPrev().getBody().getEarthquake().getHypocenter().getName());
                };
                Predicate<Channel> sensitivity = c -> {
                    Optional<SeismicIntensity> intensity = eew.getBody().getIntensity() != null ? SeismicIntensity.get(eew.getBody().getIntensity().getForecastMaxInt().getFrom()) : Optional.of(SeismicIntensity.UNKNOWN);
                    return intensity.map(seismicIntensity -> c.minIntensity.compareTo(seismicIntensity) <= 0).orElse(true);
                };
                EEWExecutor.this.service.sendMessage(isAlert.and(decimation).and(sensitivity), eew::createMessage);
            }
        };
        this.executor.execute(dmdataGateway);
        this.executor.scheduleAtFixedRate(new DmdataWsLivenessChecker(dmdataGateway), 30, 30, TimeUnit.SECONDS);

//        this.executor.scheduleAtFixedRate(new KmoniGateway(this.provider) {
//
//            @Override
//            public void onNewData(final KmoniEEW eew) {
//                Log.logger.info(eew.toString());
//
//                final Predicate<Channel> isAlert = c -> eew.isAlert() ? c.eewAlert : c.eewPrediction;
//                final Predicate<Channel> decimation = c -> {
//                    if (!c.eewDecimation)
//                        return true;
//                    if (eew.getPrev() == null)
//                        return true;
//                    if (eew.isInitial() || eew.isFinal())
//                        return true;
//                    if (eew.isAlert() != eew.getPrev().isAlert())
//                        return true;
//                    if (!eew.getIntensity().equals(eew.getPrev().getIntensity()))
//                        return true;
//                    if (!eew.getRegionName().equals(eew.getPrev().getRegionName()))
//                        return true;
//                    return false;
//                };
//                final Predicate<Channel> sensitivity = c -> {
//                    if (!eew.getIntensity().isPresent())
//                        return true;
//                    if (c.minIntensity.compareTo(eew.getIntensity().get()) <= 0)
//                        return true;
//                    return false;
//                };
//                EEWExecutor.this.service.sendMessage(isAlert.and(decimation).and(sensitivity), lang -> eew.createMessage(lang));
//
////                if (eew.isInitial() || eew.isFinal())
////                    EEWExecutor.this.executor.execute(new MonitorGateway(EEWExecutor.this.provider, eew) {
////
////                        Predicate<Channel> monitor = c -> c.monitor && (eew.isAlert() && c.eewAlert || !eew.isAlert() && c.eewPrediction);
////
////                        @Override
////                        public void onNewData(final Monitor data) {
////                            EEWExecutor.this.service.sendAttachment(this.monitor.and(sensitivity),
////                                    lang -> data.createMessage(lang));
////                        }
////                    });
//            }
//        }, 0, this.config.getKyoshinDelay(), TimeUnit.SECONDS);

        this.executor.scheduleAtFixedRate(new QuakeInfoGateway() {

            @Override
            public void onNewData(final DetailQuakeInfo data) {
                Log.logger.info(data.toString());

                final Predicate<Channel> quakeInfo = c -> c.quakeInfo;
                final Predicate<Channel> sensitivity = c -> c.minIntensity.compareTo(data.getEarthquake().getIntensity()) <= 0;
                EEWExecutor.this.service.sendMessage(quakeInfo.and(sensitivity), lang -> data.createMessage(lang));
            }
        }, 0, this.config.getQuakeInfoDelay(), TimeUnit.SECONDS);
    }

}
