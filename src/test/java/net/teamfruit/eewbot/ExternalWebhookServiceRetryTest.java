package net.teamfruit.eewbot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(10)
class ExternalWebhookServiceRetryTest {

    private static final String URL = "https://webhook.example/notify?key=secret";
    private final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
    private final List<HttpRequest> requests = new ArrayList<>();
    private final ExternalData data = mock(ExternalData.class);
    private final HttpClient client = mock(HttpClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(EEWBot.class);
    private Function<HttpRequest, CompletableFuture<HttpResponse<Void>>> respond = request ->
            CompletableFuture.completedFuture(response(204, null));
    private ExternalWebhookService service;

    @BeforeEach
    void setUp() {
        this.logs.start();
        this.logger.addAppender(this.logs);
        when(this.data.getDataType()).thenReturn("quake_info");
        when(this.data.getRawData()).thenReturn("<report/>");
        when(this.data.toExternalDto()).thenReturn(Map.of("eventId", "test-event"));
        when(this.client.<Void>sendAsync(any(), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            this.requests.add(request);
            return this.respond.apply(request);
        });
        ConfigV2 config = new ConfigV2();
        config.getExternalWebhook().setUrls(List.of(URL, "https://success.example/notify"));
        this.service = new ExternalWebhookService(config, this.client, this.scheduler);
    }

    @AfterEach
    void tearDown() {
        this.service.shutdown();
        this.logger.detachAppender(this.logs);
        MDC.clear();
    }

    @Test
    void retriesOnlyFailedDestinationWithIdenticalPayloadAndTimestamp() {
        AtomicInteger failures = new AtomicInteger();
        this.respond = request -> CompletableFuture.completedFuture(response(
                request.uri().getHost().equals("webhook.example") && failures.incrementAndGet() < 3 ? 503 : 204, null));
        MDC.put("event.id", "test-event");
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ZERO);
        assertThat(this.requests).hasSize(2);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(1));
        assertThat(this.requests).hasSize(3);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(2));
        assertThat(this.requests).hasSize(4);
        assertThat(this.requests.stream().filter(request -> request.uri().getHost().equals("success.example"))).hasSize(1);
        assertThat(this.requests.stream().map(ExternalWebhookServiceRetryTest::body)).containsOnly(body(this.requests.getFirst()));
        assertThat(this.requests).allSatisfy(request -> assertThat(request.timeout()).contains(Duration.ofSeconds(10)));
        verify(this.data).toExternalDto();
        assertThat(this.logs.list).filteredOn(log -> log.getFormattedMessage().startsWith("Retrying delivery"))
                .hasSize(2).allSatisfy(log -> assertThat(log.getMDCPropertyMap()).containsEntry("event.id", "test-event"));
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void stopsAfterThreeAttemptsAndLogsFailureWithoutLeakingUrlCredentials(int status) {
        this.respond = request -> CompletableFuture.completedFuture(response(status, null));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofMinutes(1));
        assertThat(this.requests).hasSize(6); // Three attempts per destination.
        assertThat(this.logs.list).anySatisfy(log -> assertThat(log.getFormattedMessage())
                .contains("External webhook delivery failed", "HTTP " + status));
        assertThat(this.logs.list).noneMatch(log -> log.getFormattedMessage().contains("secret"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 413})
    void permanentHttpErrorsAreNotRetried(int status) {
        this.respond = request -> CompletableFuture.completedFuture(response(status, null));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofMinutes(1));
        assertThat(this.requests).hasSize(2);
        assertThat(this.logs.list).filteredOn(log -> log.getFormattedMessage().startsWith("External webhook delivery failed"))
                .hasSize(2);
    }

    @Test
    void transportExceptionsAreRetriedAndTheirMessagesDoNotLeakCredentials() {
        AtomicInteger attempts = new AtomicInteger();
        this.respond = request -> attempts.incrementAndGet() <= 2
                ? CompletableFuture.failedFuture(new CompletionException(new IOException("Connection failed: " + URL)))
                : CompletableFuture.completedFuture(response(204, null));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(1));
        assertThat(this.requests).hasSize(4);
        assertThat(this.logs.list).noneMatch(log -> log.getFormattedMessage().contains("secret"));
    }

    @Test
    void rateLimitRetryAfterIsRespected() {
        this.respond = request -> CompletableFuture.completedFuture(response(429, "4.001"));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(4));
        assertThat(this.requests).hasSize(2);
        this.scheduler.advanceTimeBy(Duration.ofMillis(1));
        assertThat(this.requests).hasSize(4);
        this.scheduler.advanceTimeBy(Duration.ofMillis(4001));
        assertThat(this.requests).hasSize(6);
    }

    @Test
    void retryAfterHttpDateIsRespected() {
        String retryAfter = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(Instant.now().plusSeconds(10), ZoneOffset.UTC));
        this.respond = request -> CompletableFuture.completedFuture(response(503, retryAfter));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(1));
        assertThat(this.requests).hasSize(2);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(10));
        assertThat(this.requests).hasSize(4);
    }

    @Test
    void excessivelyLongRetryAfterStopsRatherThanRetryingBeforeServerAllows() {
        this.respond = request -> CompletableFuture.completedFuture(response(429, "601"));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofHours(1));
        assertThat(this.requests).hasSize(2);
        assertThat(this.logs.list).anySatisfy(log -> assertThat(log.getFormattedMessage()).contains("retry wait exceeds limit"));
    }

    @Test
    void fullBodyTimeoutCancelsHttpExchangeAndSchedulesLimitedRetry() {
        CompletableFuture<HttpResponse<Void>> stalled = new CompletableFuture<>();
        this.respond = request -> request.uri().getHost().equals("webhook.example") && this.requests.size() == 1
                ? stalled : CompletableFuture.completedFuture(response(204, null));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(10));
        assertThat(stalled.isCancelled()).isTrue();
        assertThat(this.requests).hasSize(2);
        this.scheduler.advanceTimeBy(Duration.ofSeconds(1));
        assertThat(this.requests).hasSize(3);
    }

    @Test
    void shutdownCancelsInFlightHttpAndRefusesNewDeliveries() {
        List<CompletableFuture<HttpResponse<Void>>> pending = new ArrayList<>();
        this.respond = request -> {
            CompletableFuture<HttpResponse<Void>> future = new CompletableFuture<>();
            pending.add(future);
            return future;
        };
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ZERO);
        this.service.shutdown();
        assertThat(pending).hasSize(2).allSatisfy(future -> assertThat(future.isCancelled()).isTrue());
        this.service.sendExternalWebhook(this.data);
        assertThat(this.requests).hasSize(2);
        verify(this.data).toExternalDto();
    }

    @Test
    void shutdownCancelsScheduledRetries() {
        this.respond = request -> CompletableFuture.completedFuture(response(503, null));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ZERO);
        this.service.shutdown();
        this.scheduler.advanceTimeBy(Duration.ofMinutes(1));
        assertThat(this.requests).hasSize(2);
    }

    @Test
    void serializationFailureIsLoggedWithoutSendingOrRetrying() {
        when(this.data.toExternalDto()).thenThrow(new IllegalArgumentException("bad payload"));
        this.service.sendExternalWebhook(this.data);
        this.scheduler.advanceTimeBy(Duration.ofMinutes(1));
        assertThat(this.requests).isEmpty();
        assertThat(this.logs.list).anySatisfy(log -> assertThat(log.getFormattedMessage()).contains("Failed to serialize external data"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Void> response(int status, String retryAfter) {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(retryAfter != null ? Map.of("Retry-After", List.of(retryAfter)) : Map.of(),
                (name, value) -> true));
        return response;
    }

    private static String body(HttpRequest request) {
        CompletableFuture<String> result = new CompletableFuture<>();
        StringBuilder text = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override
            public void onNext(ByteBuffer item) { text.append(StandardCharsets.UTF_8.decode(item)); }
            @Override
            public void onError(Throwable error) { result.completeExceptionally(error); }
            @Override
            public void onComplete() { result.complete(text.toString()); }
        });
        return result.join();
    }
}
