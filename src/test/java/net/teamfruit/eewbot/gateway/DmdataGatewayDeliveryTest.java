package net.teamfruit.eewbot.gateway;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.EEWService;
import net.teamfruit.eewbot.ExternalWebhookService;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        ExecutorService messageExecutor = mock(ExecutorService.class);
        when(messageExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        });
        GatewayManager manager = new GatewayManager(service, new ConfigV2(), 1L, scheduler, scheduler,
                null, null, null, null, this.externalWebhookService, messageExecutor);
        this.gateway = new DmdataGateway(null, null, 1L, true, update -> {
            this.updates.add(update);
            manager.handleDmdataEEW(update);
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
        return serial(fixture("forecast_normal.xml"), 8)
                .replace("</Body>", "<NextAdvisory>最終報</NextAdvisory></Body>");
    }

    private void receive(String xml) {
        receive(this.socket1, xml);
    }

    private void receive(DmdataGateway.WebSocketConnection.WebSocketListener socket, String xml) {
        String message = Codecs.GSON.toJson(Map.of("type", "data", "version", "2.0",
                "head", Map.of("type", "VXSE45", "test", false), "body", xml));
        socket.onText(this.webSocket, message, true);
    }

    private DeliveryPartition lastDelivery() {
        return this.deliveries.getLast();
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
        receive(serial(fixture("forecast_normal.xml"), 4));
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
        receive(serial(fixture("cancel.xml"), 30));

        assertThat(this.updates).hasSize(2);
        assertThat(this.deliveries).hasSize(2);
        assertThat(lastDelivery().direct()).containsKey(1L);
        assertThat(this.updates.getLast().maxIntensityBefore()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        verify(this.externalWebhookService, times(2)).sendExternalWebhook(any(EEW.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, 8, 13})
    void normalReportsAfterFinalOrCancellationAcceptOnlySerialIncreases(int cancelSerial) throws Exception {
        receive(fixture("final.xml"));
        if (cancelSerial >= 0)
            receive(serial(fixture("cancel_same_serial.xml"), cancelSerial));
        int lastSerial = cancelSerial >= 0 ? cancelSerial : 8;
        int acceptedBefore = this.updates.size();

        receive(serial(fixture("forecast_normal.xml"), lastSerial));
        receive(serial(fixture("forecast_normal.xml"), lastSerial - 1));
        assertThat(this.updates).hasSize(acceptedBefore);
        assertThat(this.deliveries).hasSize(acceptedBefore);

        receiveOnBothSockets(serial(fixture("forecast_normal.xml"), lastSerial + 1));
        assertThat(this.updates).hasSize(acceptedBefore + 1);
        assertThat(this.deliveries).hasSize(acceptedBefore + 1);
        assertThat(this.updates.getLast().prev().getSerial()).isEqualTo(Integer.toString(lastSerial));
        assertThat(this.updates.getLast().maxIntensityEEW()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(this.updates.getLast().deliveryHistory().warningMaxIntensity()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(lastDelivery().direct()).containsOnlyKeys(2L, 4L, 9L, 10L);
        assertThat(lastDelivery().webhook()).containsOnlyKeys(3L);

        receive(serial(fixture("forecast_normal.xml"), lastSerial + 2));
        assertThat(lastDelivery().direct()).containsOnlyKeys(4L, 10L);
        assertThat(lastDelivery().webhook()).isEmpty();
        verify(this.externalWebhookService, times(acceptedBefore + 2)).sendExternalWebhook(any(EEW.class));
    }

    @Test
    void normalReportAfterCancellationCanBeCancelledAgainUsingPreservedHistory() throws IOException {
        receive(fixture("cancel_same_serial.xml"));
        receive(serial(fixture("forecast_normal.xml"), 9));
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
                receive(socket, xml);
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
        receive(serial(fixture("forecast_normal.xml"), 1));
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
