package net.teamfruit.eewbot.gateway;

import discord4j.core.GatewayDiscordClient;
import net.teamfruit.eewbot.*;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEW;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.VTSE41;
import net.teamfruit.eewbot.entity.other.KmoniEEW;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.*;

public class GatewayManager implements AutoCloseable {

    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService messageExecutor;
    private final TimeProvider timeProvider;
    private final EEWService service;
    private final ConfigV2 config;
    private final long applicationId;
    private final HttpClient httpClient;
    private final QuakeInfoStore quakeInfoStore;
    private final ExternalWebhookService externalWebhookService;

    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
    private DmdataGateway dmdataGateway;

    public GatewayManager(
            EEWService service,
            ConfigV2 config,
            long applicationId,
            ScheduledExecutorService scheduledExecutor,
            HttpClient httpClient,
            GatewayDiscordClient client,
            DestinationAdminRegistry adminRegistry,
            QuakeInfoStore quakeInfoStore,
            ExternalWebhookService externalWebhookService
    ) {
        this(service, config, applicationId, scheduledExecutor, httpClient, client, adminRegistry, quakeInfoStore, externalWebhookService,
                Executors.newVirtualThreadPerTaskExecutor(),
                new TimeProvider(scheduledExecutor, config.getLegacy().getNtpServer()));
    }

    GatewayManager(
            EEWService service,
            ConfigV2 config,
            long applicationId,
            ScheduledExecutorService scheduledExecutor,
            HttpClient httpClient,
            GatewayDiscordClient client,
            DestinationAdminRegistry adminRegistry,
            QuakeInfoStore quakeInfoStore,
            ExternalWebhookService externalWebhookService,
            ExecutorService messageExecutor,
            TimeProvider timeProvider
    ) {
        this.service = service;
        this.config = config;
        this.applicationId = applicationId;
        this.scheduledExecutor = scheduledExecutor;
        this.httpClient = httpClient;
        this.quakeInfoStore = quakeInfoStore;
        this.externalWebhookService = externalWebhookService;
        this.messageExecutor = messageExecutor;
        this.timeProvider = timeProvider;
    }

    public TimeProvider getTimeProvider() {
        return this.timeProvider;
    }

    public void init() {
        initEEWGateway();
        initQuakeInfoGateway();
        initJMAXmlGateways();
        initWebhookSenderHealth();
    }

    private void initEEWGateway() {
        if (this.config.getLegacy().isEnableKyoshin()) {
            this.timeProvider.init();

            KmoniGateway kmoniGateway = new KmoniGateway(this.httpClient, this.timeProvider, this::handleKmoniEEW);
            this.scheduledTasks.add(
                    this.scheduledExecutor.scheduleAtFixedRate(kmoniGateway, 0, this.config.getLegacy().getKyoshinDelay(), TimeUnit.SECONDS)
            );
        } else {
            DmdataAPI dmdataAPI = new DmdataAPI(this.httpClient, this.config.getDmdata().getAPIKey(), this.config.getDmdata().getOrigin());
            this.dmdataGateway = new DmdataGateway(this.httpClient, dmdataAPI, this.applicationId, this.config.getDmdata().isMultiSocketConnect(), this::handleDmdataEEW, this.scheduledExecutor);
            this.scheduledExecutor.execute(this.dmdataGateway);
            this.scheduledTasks.add(
                    this.scheduledExecutor.scheduleAtFixedRate(new DmdataWsLivenessChecker(this.dmdataGateway), 30, 30, TimeUnit.SECONDS)
            );
        }
    }

    private void handleKmoniEEW(KmoniEEW eew) {
        MDC.put("gateway", "kmoni");
        MDC.put("event.type", "eew");
        try {
            Log.logger.info(eew.toString());
            boolean isWarning = EEWFilterClassifier.isKmoniWarning(eew);
            boolean isImportant = EEWFilterClassifier.isKmoniImportant(eew);
            SeismicIntensity maxIntensity = eew.getMaxIntensityEEW();
            ChannelFilter filter = EEWFilterClassifier.classifyEEW(isWarning, isImportant, maxIntensity);
            submitMessage(() -> this.service.sendMessage(filter, eew));
        } finally {
            MDC.clear();
        }
    }

    private void handleDmdataEEW(DmdataEEW eew) {
        MDC.put("gateway", "dmdata");
        MDC.put("event.type", "eew");
        MDC.put("event.id", eew.getEventId());
        try {
            if (eew.getBody().getEarthquake() != null &&
                    Strings.CS.equals(eew.getBody().getEarthquake().getCondition(), "仮定震源要素") &&
                    eew.getBody().getIntensity() == null)
                return;

            boolean isWarning = EEWFilterClassifier.isDmdataWarning(eew);
            boolean isImportant = EEWFilterClassifier.isDmdataImportant(eew);
            SeismicIntensity maxIntensity = eew.getMaxIntensityEEW();
            ChannelFilter filter = EEWFilterClassifier.classifyEEW(isWarning, isImportant, maxIntensity);
            submitMessage(() -> {
                this.service.sendMessage(filter, eew);
                this.externalWebhookService.sendExternalWebhook(eew);
            });
        } finally {
            MDC.clear();
        }
    }

    private void initQuakeInfoGateway() {
        if (this.config.getLegacy().isEnableLegacyQuakeInfo()) {
            QuakeInfoGateway quakeInfoGateway = new QuakeInfoGateway(data -> {
                MDC.put("gateway", "quake-info");
                MDC.put("event.type", "quake-info");
                try {
                    Log.logger.info(data.toString());
                    ChannelFilter filter = EEWFilterClassifier.classifyQuakeInfo(data.getEarthquake().getIntensity());
                    submitMessage(() -> this.service.sendMessage(filter, data));
                } finally {
                    MDC.clear();
                }
            });
            this.scheduledTasks.add(
                    this.scheduledExecutor.scheduleAtFixedRate(quakeInfoGateway, 0, this.config.getLegacy().getLegacyQuakeInfoDelay(), TimeUnit.SECONDS)
            );
        }
    }

    private void initJMAXmlGateways() {
        int currentSecond = Calendar.getInstance().get(Calendar.SECOND);
        int jmaXMLInitialDelay = 20 - currentSecond;
        if (jmaXMLInitialDelay < 0) {
            jmaXMLInitialDelay += 60;
        }

        JMAXmlGateway jmaXmlGateway = new JMAXmlGateway(this.httpClient, this.quakeInfoStore, this::handleJMAReport);
        this.scheduledTasks.add(
                this.scheduledExecutor.scheduleAtFixedRate(jmaXmlGateway, jmaXMLInitialDelay, 60, TimeUnit.SECONDS)
        );

        this.scheduledExecutor.execute(new JMAXmlLGateway(this.httpClient, this.quakeInfoStore));
    }

    private void handleJMAReport(AbstractJMAReport data) {
        MDC.put("gateway", "jma-xml");
        MDC.put("event.id", data.getEventId());
        if (data instanceof VTSE41) {
            MDC.put("event.type", "tsunami");
        } else if (data instanceof QuakeInfo) {
            MDC.put("event.type", "quake-info");
        } else {
            MDC.put("event.type", "report");
        }
        try {
            if (!this.config.getLegacy().isEnableLegacyQuakeInfo()) {
                if (data instanceof QuakeInfo quakeInfo) {
                    ChannelFilter filter = EEWFilterClassifier.classifyQuakeInfo(quakeInfo.getQuakeInfoMaxInt().orElse(SeismicIntensity.UNKNOWN));
                    submitMessage(() -> this.service.sendMessage(filter, data));
                }
            }
            if (data instanceof VTSE41) {
                ChannelFilter filter = EEWFilterClassifier.classifyTsunami();
                submitMessage(() -> this.service.sendMessage(filter, data));
            }
            if (data instanceof ExternalData externalData) {
                submitMessage(() -> this.externalWebhookService.sendExternalWebhook(externalData));
            }
        } finally {
            MDC.clear();
        }
    }

    private void initWebhookSenderHealth() {
        if (StringUtils.isNotEmpty(this.config.getWebhookSender().getAddress())) {
            this.scheduledTasks.add(
                    this.scheduledExecutor.scheduleAtFixedRate(this.service::handleWebhookSenderNotFounds, 15, 15, TimeUnit.SECONDS)
            );
        }
    }

    private void submitMessage(Runnable task) {
        Runnable wrapped = MdcUtil.wrapWithMdc(task);
        try {
            this.messageExecutor.submit(wrapped);
        } catch (RejectedExecutionException e) {
            Log.logger.debug("Message dispatch skipped (executor shut down)");
        }
    }

    @Override
    public void close() {
        if (this.dmdataGateway != null) {
            this.dmdataGateway.close();
        }

        for (ScheduledFuture<?> task : this.scheduledTasks) {
            task.cancel(true);
        }

        this.timeProvider.stop();

        this.messageExecutor.shutdown();
        try {
            if (!this.messageExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                this.messageExecutor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            this.messageExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
