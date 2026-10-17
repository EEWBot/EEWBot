package net.teamfruit.eewbot.gateway;

import discord4j.core.GatewayDiscordClient;
import net.teamfruit.eewbot.*;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import net.teamfruit.eewbot.entity.jma.telegram.VTSE41;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE52;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE53;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE61;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.*;

public class GatewayManager implements AutoCloseable {

    private final ScheduledExecutorService scheduledExecutor;
    private final ScheduledExecutorService dmdataReconnectExecutor;
    private final ExecutorService messageExecutor;
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
            ScheduledExecutorService dmdataReconnectExecutor,
            HttpClient httpClient,
            GatewayDiscordClient client,
            DestinationAdminRegistry adminRegistry,
            QuakeInfoStore quakeInfoStore,
            ExternalWebhookService externalWebhookService
    ) {
        this(service, config, applicationId, scheduledExecutor, dmdataReconnectExecutor, httpClient, client, adminRegistry, quakeInfoStore, externalWebhookService,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    GatewayManager(
            EEWService service,
            ConfigV2 config,
            long applicationId,
            ScheduledExecutorService scheduledExecutor,
            ScheduledExecutorService dmdataReconnectExecutor,
            HttpClient httpClient,
            GatewayDiscordClient client,
            DestinationAdminRegistry adminRegistry,
            QuakeInfoStore quakeInfoStore,
            ExternalWebhookService externalWebhookService,
            ExecutorService messageExecutor
    ) {
        this.service = service;
        this.config = config;
        this.applicationId = applicationId;
        this.scheduledExecutor = scheduledExecutor;
        this.dmdataReconnectExecutor = dmdataReconnectExecutor;
        this.httpClient = httpClient;
        this.quakeInfoStore = quakeInfoStore;
        this.externalWebhookService = externalWebhookService;
        this.messageExecutor = messageExecutor;
    }

    public void init() {
        initEEWGateway();
        initJMAXmlGateways();
        initWebhookSenderHealth();
    }

    private void initEEWGateway() {
        DmdataAPI dmdataAPI = new DmdataAPI(this.httpClient, this.config.getDmdata().getAPIKey(), this.config.getDmdata().getOrigin());
        String wsBaseTest = this.config.getDebug().getDmdataWsBaseUri();
        this.dmdataGateway = new DmdataGateway(this.httpClient, dmdataAPI, this.applicationId, this.config.getDmdata().isMultiSocketConnect(), this::handleDmdataEEW, this.dmdataReconnectExecutor, wsBaseTest);
        this.dmdataReconnectExecutor.execute(this.dmdataGateway);

        AbstractDmdataWsLivenessChecker livenessChecker;
        if (StringUtils.isNotEmpty(wsBaseTest)) {
            Log.logger.info("DMDATA WebSocket test mode: using debug liveness checker (DMDATA API dead check disabled)");
            livenessChecker = new DmdataWsDebugLivenessChecker(this.dmdataGateway, this.dmdataReconnectExecutor);
        } else {
            livenessChecker = new DmdataWsLivenessChecker(this.dmdataGateway, this.dmdataReconnectExecutor);
        }

        this.scheduledTasks.add(
                this.scheduledExecutor.scheduleAtFixedRate(livenessChecker, 30, 30, TimeUnit.SECONDS)
        );
    }

    void handleDmdataEEW(DmdataEEWUpdate update) {
        EEW eew = update.current();
        MDC.put("gateway", "dmdata");
        MDC.put("event.type", "eew");
        MDC.put("event.id", eew.getEventId());
        try {
            if (!update.isDeliveryTarget())
                return;

            if (eew.isCancelReport()) {
                if (update.deliveryHistory().isEmpty())
                    Log.logger.warn("DMDATA cancellation without delivery history: eventId={}, serial={}; using unfiltered EEW subscriptions",
                            eew.getEventId(), eew.getSerial());
                List<ChannelFilter> filters = EEWFilterClassifier.classifyDmdataCancellation(update.deliveryHistory());
                submitMessageOrThrow(() -> {
                    this.service.sendMessage(filters, eew);
                    this.externalWebhookService.sendExternalWebhook(eew);
                });
                return;
            }

            boolean isWarning = EEWFilterClassifier.isDmdataWarning(eew, update.prev());
            boolean isImportant = EEWFilterClassifier.isDmdataImportant(eew, update.prev());
            SeismicIntensity maxIntensity = update.maxIntensityEEW();
            ChannelFilter filter = EEWFilterClassifier.classifyEEW(isWarning, isImportant, maxIntensity);
            submitMessageOrThrow(() -> {
                this.service.sendMessage(filter, eew);
                this.externalWebhookService.sendExternalWebhook(eew);
            });
        } finally {
            MDC.clear();
        }
    }

    private void initJMAXmlGateways() {
        int currentSecond = Calendar.getInstance().get(Calendar.SECOND);
        int jmaXMLInitialDelay = 20 - currentSecond;
        if (jmaXMLInitialDelay < 0) {
            jmaXMLInitialDelay += 60;
        }

        int debugInterval = this.config.getDebug().getJmaXmlPollIntervalSeconds();
        int pollIntervalSeconds = debugInterval > 0 ? debugInterval : 60;

        JMAXmlGateway jmaXmlGateway = new JMAXmlGateway(this.httpClient, this.quakeInfoStore, this::handleJMAReport, this.config.getDebug().getJmaXmlFeedRoot());
        this.scheduledTasks.add(
                this.scheduledExecutor.scheduleAtFixedRate(jmaXmlGateway, jmaXMLInitialDelay, pollIntervalSeconds, TimeUnit.SECONDS)
        );

        this.scheduledExecutor.execute(new JMAXmlLGateway(this.httpClient, this.quakeInfoStore, this.config.getDebug().getJmaXmlFeedRoot()));
    }

    void handleJMAReport(AbstractJMAReport data) {
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
            ChannelFilter filter = data instanceof QuakeInfo quakeInfo
                    ? classifyQuakeInfo(data, quakeInfo)
                    : data instanceof VTSE41 ? EEWFilterClassifier.classifyTsunami() : null;
            // Accept both destinations in one submission so a rejection cannot partially accept a report.
            submitMessageOrThrow(() -> {
                if (filter != null)
                    this.service.sendMessage(filter, data);
                if (data instanceof ExternalData externalData)
                    this.externalWebhookService.sendExternalWebhook(externalData);
            });
            if (data instanceof QuakeInfo quakeInfo && !data.isCancelReport())
                this.quakeInfoStore.recordDelivery(quakeInfo, filter.getIntensity());
        } finally {
            MDC.clear();
        }
    }

    private ChannelFilter classifyQuakeInfo(AbstractJMAReport report, QuakeInfo quakeInfo) {
        if (!report.isCancelReport()) {
            SeismicIntensity intensity = quakeInfo.getQuakeInfoMaxInt().orElse(null);
            boolean missingObservedIntensity = report instanceof VXSE53 && intensity == null;
            boolean missingReferenceIntensity = (report instanceof VXSE61 || report instanceof VXSE52)
                    && (intensity == null || intensity == SeismicIntensity.UNKNOWN);
            if (missingObservedIntensity || missingReferenceIntensity) {
                Log.logger.warn("JMA intensity unavailable: eventId={}, type={}; ignoring intensity filter",
                        report.getEventId(), JMAXmlType.from(report.getClass()));
                return EEWFilterClassifier.classifyQuakeInfo(null);
            }
            return EEWFilterClassifier.classifyQuakeInfo(intensity != null ? intensity : SeismicIntensity.UNKNOWN);
        }
        JMAXmlType type = JMAXmlType.from(report.getClass());
        QuakeInfoStore.DeliveryHistory history = this.quakeInfoStore.getDeliveryHistory(report.getEventId(), type);
        if (!history.complete())
            Log.logger.warn("JMA cancellation without complete delivery history: eventId={}, type={}, reason={}; ignoring intensity filter",
                    report.getEventId(), type, history.reason());
        return EEWFilterClassifier.classifyQuakeInfoCancellation(history);
    }

    private void initWebhookSenderHealth() {
        if (StringUtils.isNotEmpty(this.config.getWebhookSender().getAddress())) {
            this.scheduledTasks.add(
                    this.scheduledExecutor.scheduleAtFixedRate(this.service::handleWebhookSenderNotFounds, 15, 15, TimeUnit.SECONDS)
            );
        }
    }

    private void submitMessageOrThrow(Runnable task) {
        this.messageExecutor.submit(MdcUtil.wrapWithMdc(task));
    }

    @Override
    public void close() {
        // 1. Cancel pending reconnects via DmdataGateway.close()
        if (this.dmdataGateway != null) {
            this.dmdataGateway.close();
        }

        // 2. Cancel scheduled tasks (stops liveness checker from firing again)
        for (ScheduledFuture<?> task : this.scheduledTasks) {
            task.cancel(true);
        }

        // 3. Drain in-flight reconnects on the dedicated executor
        if (this.dmdataReconnectExecutor != null) {
            this.dmdataReconnectExecutor.shutdown();
            try {
                if (!this.dmdataReconnectExecutor.awaitTermination(5, TimeUnit.SECONDS))
                    this.dmdataReconnectExecutor.shutdownNow();
            } catch (InterruptedException e) {
                this.dmdataReconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 4. Shutdown message executor
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
