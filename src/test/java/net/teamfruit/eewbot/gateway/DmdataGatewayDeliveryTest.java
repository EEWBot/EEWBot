package net.teamfruit.eewbot.gateway;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.EEWService;
import net.teamfruit.eewbot.ExternalWebhookService;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DmdataGatewayDeliveryTest {

    private final AtomicLong time = new AtomicLong();
    private final List<DmdataEEWUpdate> updates = new CopyOnWriteArrayList<>();
    private final List<DeliveryPartition> deliveries = new CopyOnWriteArrayList<>();
    private final ExternalWebhookService externalWebhookService = mock(ExternalWebhookService.class);
    private final WebSocket webSocket = mock(WebSocket.class);
    private final ConcurrentMap<String, String> telegramIds = new ConcurrentHashMap<>();
    private ExecutorService messageExecutor;
    private DmdataGateway gateway;
    private DmdataGateway.WebSocketConnection.WebSocketListener socket1;
    private DmdataGateway.WebSocketConnection.WebSocketListener socket2;

    @BeforeEach
    void setUp() {
        DeliverySnapshot snapshot = new DeliverySnapshot(1, List.of(
                channel(1, true, false, true, SeismicIntensity.FIVE_MINUS, false),
                channel(2, false, true, true, SeismicIntensity.ONE, false),
                channel(3, true, true, true, SeismicIntensity.ONE, true),
                channel(4, true, true, false, SeismicIntensity.ONE, false),
                channel(5, true, false, false, SeismicIntensity.SEVEN, false),
                channel(6, false, false, false, SeismicIntensity.UNKNOWN, false),
                channel(7, false, true, true, SeismicIntensity.SEVEN, true),
                channel(8, true, false, false, SeismicIntensity.ONE, true),
                channel(9, false, true, true, SeismicIntensity.FIVE_MINUS, false),
                channel(10, false, true, false, SeismicIntensity.ONE, false)
        ));
        DestinationDeliveryRegistry registry = snapshot::getPartitionedByWebhook;
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        EEWService service = spy(new EEWService(null, registry, null, null, null, null,
                scheduler, null, new ConfigV2()));
        // Exercise real selection/union code, intercept only the final transport boundary.
        doAnswer(invocation -> {
            this.deliveries.add(invocation.getArgument(0));
            return null;
        }).when(service).sendMessage(any(DeliveryPartition.class), any(Entity.class));
        this.messageExecutor = mock(ExecutorService.class);
        when(this.messageExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        });
        GatewayManager manager = new GatewayManager(service, new ConfigV2(), 1L, scheduler, scheduler,
                null, null, null, null, this.externalWebhookService, this.messageExecutor);
        this.gateway = new DmdataGateway(null, null, 1L, true, update -> {
            manager.handleDmdataEEW(update);
            this.updates.add(update);
        }, scheduler, "", this.time::get);
        this.socket1 = this.gateway.new WebSocketConnection("test-1", "wss://test", true).getListener();
        this.socket2 = this.gateway.new WebSocketConnection("test-2", "wss://test", true).getListener();
    }

    private static DeliverySnapshot.DeliveryChannel channel(long id, boolean warning, boolean forecast,
                                                           boolean decimation, SeismicIntensity intensity, boolean webhook) {
        return new DeliverySnapshot.DeliveryChannel(id, id, null, null, warning, forecast, decimation,
                false, false, intensity, "ja_jp", webhook ? ChannelWebhook.of(id, "test-token") : null);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(DmdataGatewayDeliveryTest.class.getResourceAsStream("/dmdata/eew/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String serial(String xml, int serial) {
        return xml.replaceAll("<Serial>\\d+</Serial>", "<Serial>" + serial + "</Serial>");
    }

    private static String warning(int serial) throws IOException {
        return serial(fixture("final.xml").replaceAll("<NextAdvisory>.*?</NextAdvisory>", ""), serial);
    }

    private static String forecastFinal() throws IOException {
        return reportTime(serial(fixture("forecast_normal.xml"), 8), "2026-05-11T10:24:30+09:00")
                .replace("</Body>", "<NextAdvisory>最終報</NextAdvisory></Body>");
    }

    private static String reportTime(String xml, String time) {
        return xml.replaceAll("<ReportDateTime>.*?</ReportDateTime>", "<ReportDateTime>" + time + "</ReportDateTime>");
    }

    private static String infoType(String xml, JMAInfoType type) {
        return xml.replaceAll("<InfoType>.*?</InfoType>", "<InfoType>" + type + "</InfoType>");
    }

    private String idFor(String xml) {
        return this.telegramIds.computeIfAbsent(xml, ignored -> UUID.randomUUID().toString());
    }

    private void receive(String xml) {
        receive(this.socket1, xml);
    }

    private void receive(DmdataGateway.WebSocketConnection.WebSocketListener socket, String xml) {
        receive(socket, idFor(xml), xml);
    }

    private void receive(String id, String xml) {
        receive(this.socket1, id, xml);
    }

    private void receive(DmdataGateway.WebSocketConnection.WebSocketListener socket, String id, String xml) {
        receive(socket, id, xml, null);
    }

    private void receive(DmdataGateway.WebSocketConnection.WebSocketListener socket, String id, String body, String compression) {
        Map<String, Object> data = new HashMap<>(Map.of("type", "data", "version", "2.0",
                "head", Map.of("type", "VXSE45", "test", false), "body", body));
        if (id != null)
            data.put("id", id);
        if (compression != null)
            data.put("compression", compression);
        String message = Codecs.GSON.toJson(data);
        socket.onText(this.webSocket, message, true);
    }

    private DeliveryPartition lastDelivery() {
        return this.deliveries.getLast();
    }

    @ParameterizedTest
    @ValueSource(strings = {"xml", "gzip", "zip"})
    void knownIdSkipsBodyDecodingAndParsingAndRequestsNextFrame(String format) throws IOException {
        receive("known-id", fixture("forecast_normal.xml"));
        Logger logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // An attempted body decode/parse would fail and log an error. A known ID must bypass it.
            receive(this.socket2, "known-id", format.equals("xml") ? "<Report>" : "invalid-base64",
                    format.equals("xml") ? null : format);

            assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
            assertThat(this.updates).hasSize(1);
            assertThat(this.deliveries).hasSize(1);
            verify(this.messageExecutor).submit(any(Runnable.class));
            verify(this.externalWebhookService).sendExternalWebhook(any(EEW.class));
            verify(this.webSocket, times(2)).request(1L);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "zip"})
    void bodyDecodingFailureDoesNotPreventRetryWithSameId(String compression) throws IOException {
        receive(this.socket1, "retry-id", "invalid-base64", compression);
        assertThat(this.updates).isEmpty();
        assertThat(this.deliveries).isEmpty();
        receive(this.socket2, "retry-id", fixture("forecast_normal.xml"));

        assertThat(this.updates).hasSize(1);
        assertThat(this.updates.getLast().prev()).isNull();
        assertThat(this.deliveries).hasSize(1);
        verify(this.externalWebhookService).sendExternalWebhook(any(EEW.class));
    }

    @ParameterizedTest
    @EnumSource(JMAInfoType.class)
    void sameIdAcrossSocketsIsProcessedOnceForEveryInfoType(JMAInfoType type) throws Exception {
        String xml = infoType(fixture(type == JMAInfoType.取消 ? "cancel_same_serial.xml" : "forecast_normal.xml"), type);
        receiveOnBothSockets("shared-telegram-id", xml);
        receive(this.socket2, "shared-telegram-id", xml);

        assertThat(this.updates).hasSize(1);
        assertThat(this.deliveries).hasSize(1);
        verify(this.externalWebhookService).sendExternalWebhook(any(EEW.class));
    }

    @ParameterizedTest
    @EnumSource(JMAInfoType.class)
    void distinctIdWithSameSerialIsDeliveredIncludingDecimatedChannels(JMAInfoType type) throws IOException {
        String first = serial(fixture("forecast_normal.xml"), 8);
        String edited = type == JMAInfoType.取消 ? fixture("cancel_same_serial.xml")
                : infoType(first.replace("<Text/>", "<Text>更新内容</Text>"), type);
        receive("initial-id", first);
        receive("edited-id", edited);

        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void distinctIdsAreAcceptedEvenWithIdenticalBodies() throws IOException {
        String xml = fixture("forecast_normal.xml");
        receive("id-a", xml);
        receive("id-b", xml);
        receive(this.socket2, "id-a", xml);

        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void distinctCancellationIdsAreBothDeliveredWithPreservedHistory() throws IOException {
        receive("final-id", fixture("final.xml"));
        receive("cancel-a", fixture("cancel_same_serial.xml"));
        receive("cancel-b", fixture("cancel_same_serial.xml"));
        receive(this.socket2, "cancel-a", fixture("cancel_same_serial.xml"));
        receive(this.socket2, "cancel-b", fixture("cancel_same_serial.xml"));

        assertThat(this.updates).hasSize(3);
        assertThat(this.deliveries).hasSize(3);
        assertThat(lastDelivery().direct()).containsOnlyKeys(1L, 4L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L, 8L);
        verify(this.externalWebhookService, times(3)).sendExternalWebhook(any(EEW.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void missingIdUsesXmlFallbackWithoutConflatingDifferentBodies(String id) throws IOException {
        String xml = fixture("forecast_normal.xml");
        receive(id, xml);
        receive(this.socket2, id, xml);
        receive(id, xml.replace("<Text/>", "<Text>別の本文</Text>"));

        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void parseFailureDoesNotRegisterId() throws IOException {
        receive("retry-id", "<Report>");
        assertThat(this.updates).isEmpty();
        receive(this.socket2, "retry-id", fixture("forecast_normal.xml"));

        assertThat(this.updates).hasSize(1);
        assertThat(this.updates.getLast().prev()).isNull();
        assertThat(this.deliveries).hasSize(1);
        verify(this.externalWebhookService).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void stateCalculationFailureDoesNotRegisterId() throws IOException {
        receive("retry-id", fixture("forecast_normal.xml").replace("<Serial>2</Serial>", "<Serial>invalid</Serial>"));
        assertThat(this.updates).isEmpty();
        receive(this.socket2, "retry-id", fixture("forecast_normal.xml"));

        assertThat(this.updates).hasSize(1);
        assertThat(this.updates.getLast().prev()).isNull();
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.UNKNOWN);
        verify(this.externalWebhookService).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void enqueueFailureRetainsReceivedSerialWithoutCommittingIdOrEventState() throws IOException {
        receive("initial-id", fixture("forecast_normal.xml"));
        doThrow(new RejectedExecutionException("test rejection")).doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        }).when(this.messageExecutor).submit(any(Runnable.class));

        receive("retry-id", warning(8));
        assertThat(this.updates).hasSize(1);
        assertThat(this.deliveries).hasSize(1);
        receive("older-id", warning(7));
        assertThat(this.updates).hasSize(1);
        assertThat(this.deliveries).hasSize(1);
        receive(this.socket2, "retry-id", warning(8));
        receive("retry-id", warning(8));

        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo("2");
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(this.updates.getLast().deliveryHistory().forecastMaxIntensity()).isEqualTo(SeismicIntensity.FOUR);
        verify(this.messageExecutor, times(3)).submit(any(Runnable.class));
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void asynchronousTaskFailureAfterEnqueueDoesNotRetryTheSameId() throws IOException {
        // Submission succeeded; the returned future represents a later delivery failure.
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("delivery failed")))
                .when(this.messageExecutor).submit(any(Runnable.class));
        receive("queued-id", fixture("forecast_normal.xml"));
        receive(this.socket2, "queued-id", fixture("forecast_normal.xml"));

        assertThat(this.updates).hasSize(1);
        verify(this.messageExecutor).submit(any(Runnable.class));
        verifyNoInteractions(this.externalWebhookService);
    }

    @Test
    void receivedIdExpiresWithoutRenewalOnDuplicates() throws IOException {
        String xml = fixture("cancel_same_serial.xml");
        receive("expiring-id", xml);
        this.time.addAndGet(Duration.ofMinutes(59).toNanos());
        receive(this.socket2, "expiring-id", xml);
        assertThat(this.updates).hasSize(1);
        this.time.addAndGet(Duration.ofMinutes(2).toNanos());
        receive("expiring-id", xml);

        assertThat(this.updates).hasSize(2);
        assertThat(this.updates.getLast().prev()).isNull();
        assertThat(this.deliveries).hasSize(2);
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void expiredIdDoesNotAllowAnOlderAnnouncementWhileSerialHistoryIsRetained() throws IOException {
        receive("old-id", fixture("forecast_normal.xml"));
        this.time.addAndGet(Duration.ofMinutes(59).toNanos());
        receive("new-id", reportTime(serial(fixture("forecast_normal.xml"), 3), "2026-05-11T10:23:41+09:00"));
        this.time.addAndGet(Duration.ofMinutes(2).toNanos());
        receive("old-id", fixture("forecast_normal.xml"));
        assertThat(this.updates).hasSize(2);
        assertThat(this.updates.getLast().current().getSerial()).isEqualTo("3");
        receive("probe-id", reportTime(serial(fixture("forecast_normal.xml"), 4), "2026-05-11T10:23:42+09:00"));

        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo("3");
        assertThat(this.deliveries).hasSize(3);
    }

    @Test
    void earlierReportTimeDoesNotReplaceLatestButStillAdvancesReceivedSerial() throws IOException {
        receive("latest-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:26:00+09:00"));
        receive("late-id", reportTime(serial(fixture("forecast_normal.xml"), 10), "2026-05-11T10:25:00+09:00")
                .replace("<From>4</From>", "<From>2</From>"));
        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        receive("older-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:26:30+09:00"));
        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);

        receive("probe-id", reportTime(serial(fixture("forecast_normal.xml"), 11), "2026-05-11T10:27:00+09:00"));
        EEW previous = this.updates.getLast().prev();
        assertThat(previous.getSerial()).isEqualTo("9");
        assertThat(previous.getReportDateTime()).isEqualTo(Instant.parse("2026-05-11T01:26:00Z"));
        assertThat(previous.getForecastMaxInt().from()).isEqualTo("4");
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(this.updates.getLast().deliveryHistory().forecastMaxIntensity()).isEqualTo(SeismicIntensity.FOUR);
    }

    @Test
    void lowerSerialAnnouncementWithLaterTimeIsDiscardedWithoutChangingStateOrIntensityHistory() throws IOException {
        receive("latest-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:26:00+09:00"));
        receive("lower-id", reportTime(warning(8), "2026-05-11T10:27:00+09:00"));
        assertThat(this.updates).hasSize(1);
        assertThat(this.deliveries).hasSize(1);
        receive("probe-id", reportTime(serial(fixture("forecast_normal.xml"), 10), "2026-05-11T10:28:00+09:00"));

        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo("9");
        assertThat(this.updates.getLast().prev().getReportDateTime()).isEqualTo(Instant.parse("2026-05-11T01:26:00Z"));
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(this.updates.getLast().deliveryHistory().warningMaxIntensity()).isNull();
        assertThat(this.updates.getLast().deliveryHistory().forecastMaxIntensity()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        verify(this.messageExecutor, times(2)).submit(any(Runnable.class));
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @ParameterizedTest
    @EnumSource(value = JMAInfoType.class, names = {"訂正", "取消"})
    void lowerSerialCorrectionOrCancellationDoesNotRollBackAnnouncementSerial(JMAInfoType type) throws IOException {
        receive("initial-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:26:00+09:00"));
        String revised = type == JMAInfoType.取消 ? fixture("cancel_same_serial.xml") : fixture("forecast_normal.xml");
        receive("revised-id", reportTime(infoType(serial(revised, 2), type), "2026-05-11T10:27:00+09:00"));
        receive("older-id", reportTime(warning(8), "2026-05-11T10:27:30+09:00"));
        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        receive("probe-id", reportTime(serial(fixture("forecast_normal.xml"), 10), "2026-05-11T10:28:00+09:00"));

        EEW previous = this.updates.getLast().prev();
        assertThat(previous.getSerial()).isEqualTo("2");
        assertThat(previous.getInfoType()).isEqualTo(type);
        assertThat(previous.getReportDateTime()).isEqualTo(Instant.parse("2026-05-11T01:27:00Z"));
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(this.updates.getLast().deliveryHistory().warningMaxIntensity()).isNull();
        assertThat(this.deliveries).hasSize(3);
        receive("correction-id", reportTime(infoType(serial(fixture("forecast_normal.xml"), 10), JMAInfoType.訂正),
                "2026-05-11T10:29:00+09:00"));
        assertThat(this.updates).extracting(update -> update.current().getSerial()).containsExactly("9", "2", "10", "10");
        assertThat(this.updates.getLast().current().getInfoType()).isEqualTo(JMAInfoType.訂正);
        assertThat(this.deliveries).hasSize(4);
        verify(this.externalWebhookService, times(4)).sendExternalWebhook(any(EEW.class));
    }

    @ParameterizedTest
    @EnumSource(value = JMAInfoType.class, names = {"訂正", "取消"})
    void higherSerialCorrectionOrCancellationDoesNotAdvanceAnnouncementSerial(JMAInfoType type) throws IOException {
        receive("initial-id", serial(fixture("forecast_normal.xml"), 9));
        String revised = type == JMAInfoType.取消 ? fixture("cancel_same_serial.xml") : fixture("forecast_normal.xml");
        receive("revised-id", reportTime(infoType(serial(revised, 13), type), "2026-05-11T10:27:00+09:00"));
        receive("next-id", reportTime(serial(fixture("forecast_normal.xml"), 10), "2026-05-11T10:28:00+09:00"));

        assertThat(this.updates).extracting(update -> update.current().getSerial()).containsExactly("9", "13", "10");
        receive("older-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:29:00+09:00"));
        assertThat(this.updates).hasSize(3);
        assertThat(this.deliveries).hasSize(3);
    }

    @Test
    void discardedAnnouncementIdSkipsBodyDecodingOnTheOtherSocket() throws IOException {
        receive("latest-id", serial(fixture("forecast_normal.xml"), 9));
        receive("discarded-id", serial(fixture("forecast_normal.xml"), 8));
        Logger logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            receive(this.socket2, "discarded-id", "invalid-base64", "gzip");

            assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
            assertThat(this.updates).hasSize(1);
            assertThat(this.deliveries).hasSize(1);
            verify(this.messageExecutor).submit(any(Runnable.class));
            verify(this.externalWebhookService).sendExternalWebhook(any(EEW.class));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {65, 1439})
    void announcementSerialHistorySurvivesEventStateExpiry(int minutes) throws IOException {
        receive("initial-id", serial(fixture("forecast_normal.xml"), 9));
        this.time.addAndGet(Duration.ofMinutes(minutes).toNanos());
        receive("older-id", reportTime(warning(8), "2026-05-11T10:27:00+09:00"));
        assertThat(this.updates).hasSize(1);
        assertThat(this.deliveries).hasSize(1);

        receive("next-id", reportTime(serial(fixture("forecast_normal.xml"), 10), "2026-05-11T10:28:00+09:00"));
        assertThat(this.updates).hasSize(2);
        assertThat(this.updates.getLast().prev()).isNull();
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.UNKNOWN);
        assertThat(this.updates.getLast().deliveryHistory().warningMaxIntensity()).isNull();
        this.time.addAndGet(Duration.ofMinutes(2).toNanos());
        receive("still-older-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:29:00+09:00"));
        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
    }

    @Test
    void discardedAnnouncementsAndAdministrativeReportsDoNotRenewSerialHistory() throws IOException {
        receive("initial-id", serial(fixture("forecast_normal.xml"), 9));
        this.time.addAndGet(Duration.ofHours(24).minusMinutes(1).toNanos());
        receive("older-id", serial(fixture("forecast_normal.xml"), 8));
        receive("correction-id", infoType(serial(fixture("forecast_normal.xml"), 2), JMAInfoType.訂正));
        receive("cancel-id", serial(fixture("cancel_same_serial.xml"), 2));
        assertThat(this.updates).hasSize(3);
        this.time.addAndGet(Duration.ofMinutes(2).toNanos());
        receive("after-expiry-id", reportTime(serial(fixture("forecast_normal.xml"), 8), "2026-05-11T10:28:00+09:00"));

        assertThat(this.updates).hasSize(4);
        assertThat(this.updates.getLast().current().getSerial()).isEqualTo("8");
        assertThat(this.deliveries).hasSize(4);
    }

    @Test
    void announcementSerialHistoryIsScopedToEachEvent() throws IOException {
        receive("event-a-id", serial(fixture("forecast_normal.xml"), 9));
        receive("event-b-id", fixture("forecast_normal.xml").replace("20260511102316", "event-b"));

        assertThat(this.updates).hasSize(2);
        assertThat(this.updates.getLast().current().getEventId()).isEqualTo("event-b");
        assertThat(this.updates.getLast().prev()).isNull();
        assertThat(this.deliveries).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(value = JMAInfoType.class, names = {"訂正", "取消"})
    void equalTimeAndSerialKeepCorrectionOrCancellationAheadOfAnnouncement(JMAInfoType type) throws IOException {
        String revised = type == JMAInfoType.取消 ? fixture("cancel_same_serial.xml") : serial(fixture("forecast_normal.xml"), 8);
        receive("initial-id", reportTime(serial(fixture("forecast_normal.xml"), 8), "2026-05-11T10:26:00+09:00"));
        receive("revised-id", reportTime(infoType(revised, type), "2026-05-11T10:26:00+09:00"));
        receive("announcement-id", reportTime(serial(fixture("forecast_normal.xml"), 8), "2026-05-11T10:26:00+09:00"));
        receive("probe-id", reportTime(serial(fixture("forecast_normal.xml"), 10), "2026-05-11T10:27:00+09:00"));

        assertThat(this.updates.getLast().prev().getInfoType()).isEqualTo(type);
        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo("8");
        assertThat(this.deliveries).hasSize(4);
    }

    @ParameterizedTest
    @EnumSource(value = JMAInfoType.class, names = {"訂正", "取消"})
    void equalTimeHigherSerialReplacesCorrectionOrCancellationAndNextReportUsesIt(JMAInfoType type) throws IOException {
        String revised = type == JMAInfoType.取消 ? fixture("cancel_same_serial.xml") : serial(fixture("forecast_normal.xml"), 8);
        receive("revised-id", reportTime(infoType(revised, type), "2026-05-11T10:26:00+09:00"));
        String next = serial(fixture("forecast_normal.xml"), 9).replace("<From>4</From>", "<From>2</From>");
        receive("higher-serial-id", reportTime(next, "2026-05-11T10:26:00+09:00"));
        receive("following-id", reportTime(serial(next, 10), "2026-05-11T10:27:00+09:00"));

        EEW previous = this.updates.getLast().prev();
        assertThat(previous.getSerial()).isEqualTo("9");
        assertThat(previous.getInfoType()).isEqualTo(JMAInfoType.発表);
        assertThat(previous.getForecastMaxInt().from()).isEqualTo("2");
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(type == JMAInfoType.取消 ? SeismicIntensity.TWO : SeismicIntensity.FOUR);
        // The unchanged tenth report is compared to the ninth report and decimated as usual.
        assertThat(lastDelivery().direct()).containsOnlyKeys(4L, 10L);
        assertThat(lastDelivery().webhook()).isEmpty();
        assertThat(this.deliveries).hasSize(3);
        verify(this.externalWebhookService, times(3)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void sameTimeAndSerialAnnouncementUpdatesLatestWhileKeepingRunningMaximum() throws IOException {
        String xml = serial(fixture("forecast_normal.xml"), 8);
        receive("initial-id", xml);
        receive("edited-id", xml.replace("<From>4</From>", "<From>2</From>"));
        receive("probe-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:27:00+09:00"));

        assertThat(this.updates.getLast().prev().getForecastMaxInt().from()).isEqualTo("2");
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(this.updates.getLast().deliveryHistory().forecastMaxIntensity()).isEqualTo(SeismicIntensity.FOUR);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldTerminalReportDoesNotDeactivateNewerActiveEvent(boolean cancelled) throws IOException {
        receive("latest-id", reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:26:00+09:00"));
        receive("old-terminal-id", reportTime(fixture(cancelled ? "cancel_same_serial.xml" : "final.xml"), "2026-05-11T10:25:00+09:00"));
        receive("other-event-id", fixture("forecast_normal.xml").replace("20260511102316", "event-b"));

        assertThat(this.updates.getLast().current().isConcurrent()).isTrue();
        assertThat(this.updates.getLast().current().getConcurrentIndex()).isEqualTo(2);
        receive("probe-id", reportTime(serial(fixture("forecast_normal.xml"), 10), "2026-05-11T10:27:00+09:00"));
        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo("9");
        assertThat(this.updates.getLast().prev().getForecastMaxInt().from()).isEqualTo("4");
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FOUR);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 2, 13})
    void cancellationAfterFinalAcceptsAnySerialAndUsesWarningHistory(int cancelSerial) throws IOException {
        receive(warning(2));
        receive(fixture("final.xml"));
        receive(serial(fixture("cancel_same_serial.xml"), cancelSerial));

        assertThat(this.updates).hasSize(3);
        assertThat(this.updates.getLast().prev().isLastInfo()).isTrue();
        assertThat(this.updates.getLast().deliveryHistory().warningMaxIntensity()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(lastDelivery().direct()).containsOnlyKeys(1L, 4L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L, 8L);
        assertThat(this.deliveries).hasSize(3);
        verify(this.externalWebhookService, times(3)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void warningHistorySurvivesForecastAndFinalAndBothSubscriptionsAreDeduplicated() throws IOException {
        receive(fixture("forecast_normal.xml"));
        receive(warning(3));
        receive(reportTime(serial(fixture("forecast_normal.xml"), 4), "2026-05-11T10:24:20+09:00"));
        receive(forecastFinal());
        receive(fixture("cancel_same_serial.xml"));

        DmdataEEWUpdate cancel = this.updates.getLast();
        assertThat(cancel.prev().isEEWWarning()).isFalse();
        assertThat(cancel.deliveryHistory().warningMaxIntensity()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(cancel.deliveryHistory().forecastMaxIntensity()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(lastDelivery().direct()).containsOnlyKeys(1L, 2L, 4L, 9L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L, 8L);
        assertThat(this.deliveries).hasSize(5);
        verify(this.externalWebhookService, times(5)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void cancellationUsesSeparateClassificationMaxima() throws IOException {
        receive(fixture("forecast_normal.xml"));
        receive(fixture("final.xml"));
        receive(fixture("cancel_same_serial.xml"));

        assertThat(this.updates.getLast().deliveryHistory().forecastMaxIntensity()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(lastDelivery().direct()).containsOnlyKeys(1L, 2L, 4L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L, 8L);
    }

    @Test
    void forecastOnlyCancellationDoesNotTargetWarningOnlyChannels() throws IOException {
        receive(forecastFinal());
        receive(fixture("cancel_same_serial.xml"));

        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);
    }

    @Test
    void duplicatedFinalAndCancellationAcrossSocketsRetainHistoryAndAreDeduplicated() throws IOException {
        receive(fixture("final.xml"));
        receive(this.socket2, fixture("final.xml"));
        receive(fixture("cancel_same_serial.xml"));
        receive(this.socket2, fixture("cancel_same_serial.xml"));

        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        assertThat(lastDelivery().direct()).containsKey(1L);
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, 8, 13})
    void normalReportsAfterFinalOrCancellationStillRejectOlderSerials(int cancelSerial) throws Exception {
        receive(fixture("final.xml"));
        if (cancelSerial >= 0)
            receive(serial(fixture("cancel_same_serial.xml"), cancelSerial));
        int lastSerial = cancelSerial >= 0 ? cancelSerial : 8;
        int nextSerial = Math.max(8, lastSerial) + 1;
        int acceptedBefore = this.updates.size();

        String next = reportTime(serial(fixture("forecast_normal.xml"), nextSerial), "2026-05-11T10:26:00+09:00");
        receiveOnBothSockets("normal-after-terminal", next);
        assertThat(this.updates).hasSize(acceptedBefore + 1);
        assertThat(this.deliveries).hasSize(acceptedBefore + 1);
        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo(Integer.toString(lastSerial));
        assertThat(this.updates.getLast().maxIntensityEEW()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(this.updates.getLast().deliveryHistory().warningMaxIntensity()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 9L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);

        receive("same-serial-edit", reportTime(next, "2026-05-11T10:26:01+09:00").replace("<Text/>", "<Text>更新</Text>"));
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 9L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);
        receive("older-report", serial(fixture("forecast_normal.xml"), nextSerial - 1));
        assertThat(this.updates).hasSize(acceptedBefore + 2);
        assertThat(this.deliveries).hasSize(acceptedBefore + 2);

        receive(reportTime(serial(fixture("forecast_normal.xml"), nextSerial + 1), "2026-05-11T10:26:02+09:00"));
        assertThat(lastDelivery().direct()).containsOnlyKeys(4L, 10L);
        assertThat(lastDelivery().webhook()).isEmpty();
        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo(Integer.toString(nextSerial));
        verify(this.externalWebhookService, times(acceptedBefore + 3)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void normalReportAfterCancellationCanBeCancelledAgainUsingPreservedHistory() throws IOException {
        receive(fixture("cancel_same_serial.xml"));
        receive(reportTime(serial(fixture("forecast_normal.xml"), 9), "2026-05-11T10:26:00+09:00"));
        assertThat(this.updates).hasSize(2);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);

        receive(serial(fixture("cancel_same_serial.xml"), 9));
        receive(this.socket2, serial(fixture("cancel_same_serial.xml"), 9));
        assertThat(this.updates).hasSize(3);
        assertThat(this.deliveries).hasSize(3);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);
        verify(this.externalWebhookService, times(3)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void reportWithoutHypocenterAfterCancellationIsAcceptedAndImportant() throws IOException {
        receive(fixture("cancel_same_serial.xml"));
        String normal = serial(fixture("cancel_same_serial.xml"), 9).replace("<InfoType>取消</InfoType>", "<InfoType>発表</InfoType>");
        receive(normal);

        assertThat(this.updates).hasSize(2);
        assertThat(this.updates.getLast().current().getHypocenterName()).isNull();
        assertThat(this.deliveries).hasSize(2);
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void simultaneousFinalsAndCancellationsAreEachDeliveredOnce() throws Exception {
        receive(warning(1));
        receiveOnBothSockets(fixture("final.xml"));
        receiveOnBothSockets(fixture("cancel_same_serial.xml"));

        assertThat(this.updates).hasSize(3);
        assertThat(this.deliveries).hasSize(3);
        assertThat(lastDelivery().direct()).containsKey(1L);
        verify(this.externalWebhookService, times(3)).sendExternalWebhook(any(EEW.class));
    }

    private void receiveOnBothSockets(String xml) throws Exception {
        receiveOnBothSockets(idFor(xml), xml);
    }

    private void receiveOnBothSockets(String id, String xml) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var tasks = List.of(this.socket1, this.socket2).stream().map(socket -> pool.submit(() -> {
                ready.countDown();
                try {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                receive(socket, id, xml);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> task : tasks)
                task.get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrExpiredHistoryFallsBackToAllEewSubscriptionsAndLogs(boolean expired) throws IOException {
        if (expired) {
            receive(fixture("final.xml"));
            this.time.addAndGet(Duration.ofMinutes(59).toNanos());
            receive(this.socket2, fixture("final.xml"));
            this.time.addAndGet(Duration.ofMinutes(2).toNanos());
        }
        Logger logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            receive(fixture("cancel_same_serial.xml"));
            receive(this.socket2, fixture("cancel_same_serial.xml"));

            assertThat(this.updates.getLast().prev()).isNull();
            assertThat(lastDelivery().direct()).containsOnlyKeys(1L, 2L, 4L, 5L, 9L, 10L);
            assertThat(lastDelivery().webhook()).containsOnlyKeys(3L, 7L, 8L);
            assertThat(this.deliveries).hasSize(expired ? 2 : 1);
            assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains("cancellation without delivery history", "20260511102316"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void terminalEventsDoNotCountAsConcurrentEvents() throws IOException {
        receive(fixture("final.xml"));
        String other = fixture("forecast_normal.xml").replace("20260511102316", "event-b");
        receive(other);
        assertThat(this.updates.getLast().current().isConcurrent()).isFalse();
        assertThat(this.updates.getLast().current().getConcurrentIndex()).isEqualTo(1);

        receive(fixture("cancel.xml").replace("20260511102316", "event-b"));
        receive(other.replace("event-b", "event-c"));
        assertThat(this.updates.getLast().current().isConcurrent()).isFalse();
        assertThat(this.updates.getLast().current().getConcurrentIndex()).isEqualTo(1);
    }

    @Test
    void overlappingActiveEventsKeepDistinctIndicesWhileFinalsAreRetained() throws IOException {
        receive(fixture("forecast_normal.xml"));
        receive(fixture("forecast_normal.xml").replace("20260511102316", "event-b"));
        assertThat(this.updates.getLast().current().isConcurrent()).isTrue();
        assertThat(this.updates.getLast().current().getConcurrentIndex()).isEqualTo(2);
        receive(fixture("final.xml"));
        receive(fixture("forecast_normal.xml").replace("20260511102316", "event-c"));
        assertThat(this.updates.getLast().current().isConcurrent()).isTrue();
        assertThat(this.updates.getLast().current().getConcurrentIndex()).isEqualTo(3);
    }

    @Test
    void skippedPlumReportDoesNotCreateDeliveryHistory() throws IOException {
        receive(fixture("deep.xml").replace("<Earthquake>", "<Earthquake><Condition>仮定震源要素</Condition>"));
        assertThat(this.updates).hasSize(1);
        assertThat(this.updates.getLast().isDeliveryTarget()).isFalse();
        assertThat(this.deliveries).isEmpty();
        verifyNoInteractions(this.externalWebhookService);

        receive(fixture("cancel_same_serial.xml"));
        assertThat(this.updates.getLast().prev()).isNotNull();
        assertThat(this.updates.getLast().deliveryHistory().isEmpty()).isTrue();
        assertThat(lastDelivery().direct()).containsOnlyKeys(1L, 2L, 4L, 5L, 9L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L, 7L, 8L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newerReportAfterFinalOrCancellationDoesNotReactivateTheEvent(boolean cancelled) throws IOException {
        receive(fixture(cancelled ? "cancel_same_serial.xml" : "final.xml"));
        receive(serial(fixture("forecast_normal.xml"), 9));
        receive(fixture("forecast_normal.xml").replace("20260511102316", "event-b"));

        assertThat(this.updates).hasSize(3);
        assertThat(this.updates.getLast().current().isConcurrent()).isFalse();
        assertThat(this.updates.getLast().current().getConcurrentIndex()).isEqualTo(1);
        receive(serial(fixture("cancel_same_serial.xml"), 2));
        if (cancelled) {
            assertThat(this.updates.getLast().deliveryHistory().forecastMaxIntensity()).isEqualTo(SeismicIntensity.FOUR);
            assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        } else {
            assertThat(this.updates.getLast().deliveryHistory().warningMaxIntensity()).isEqualTo(SeismicIntensity.FIVE_PLUS);
            assertThat(lastDelivery().direct()).containsKey(1L);
        }
    }

    @Test
    void normalReportsKeepSerialIntensityDecimationAndClassificationRules() throws IOException {
        receive(fixture("forecast_normal.xml"));
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);
        receive(this.socket2, fixture("forecast_normal.xml"));
        assertThat(this.updates).hasSize(1);

        receive(serial(fixture("forecast_normal.xml"), 3));
        assertThat(lastDelivery().direct()).containsOnlyKeys(4L, 10L);
        assertThat(lastDelivery().webhook()).isEmpty();

        receive(warning(4));
        assertThat(lastDelivery().direct()).containsOnlyKeys(1L, 4L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L, 8L);
        receive(serial(fixture("forecast_normal.xml"), 5));
        assertThat(this.updates.getLast().maxIntensityEEW()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 9L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);
    }
}
