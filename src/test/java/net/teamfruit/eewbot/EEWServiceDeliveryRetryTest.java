package net.teamfruit.eewbot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EEWServiceDeliveryRetryTest {

    private final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
    private final ScheduledExecutorService cleanup = mock(ScheduledExecutorService.class);
    private final DestinationAdminRegistry registry = mock(DestinationAdminRegistry.class);
    private final MessageCreateSpec first = MessageCreateSpec.builder().content("first").build();
    private final MessageCreateSpec second = MessageCreateSpec.builder().content("second").build();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(EEWBot.class);
    private EEWService service;

    @BeforeEach
    void setUp() {
        this.logs.start();
        this.logger.addAppender(this.logs);
        this.service = spy(new EEWService(null, null, this.registry, null, null, null,
                this.cleanup, null, new ConfigV2(), this.scheduler));
    }

    @AfterEach
    void tearDown() {
        this.scheduler.dispose();
        this.logger.detachAppender(this.logs);
        MDC.clear();
    }

    @Test
    void retriesOnlyFailedPageAndKeepsPreviouslySuccessfulPages() {
        AtomicInteger secondAttempts = new AtomicInteger();
        doReturn(Mono.empty()).when(this.service).directSendMessagePassErrors(123, this.first);
        doAnswer(invocation -> secondAttempts.incrementAndGet() < 3 ? Mono.error(new IOException("network")) : Mono.empty())
                .when(this.service).directSendMessagePassErrors(123, this.second);
        MDC.put("event.id", "retry-event");
        StepVerifier.create(this.service.sendMessagesInOrder(123, List.of(this.first, this.second)))
                .then(() -> assertThat(secondAttempts.get()).isEqualTo(1))
                .then(() -> this.scheduler.advanceTimeBy(Duration.ofSeconds(1)))
                .then(() -> assertThat(secondAttempts.get()).isEqualTo(2))
                .then(() -> this.scheduler.advanceTimeBy(Duration.ofSeconds(2)))
                .verifyComplete();
        verify(this.service).directSendMessagePassErrors(123, this.first);
        verify(this.service, times(3)).directSendMessagePassErrors(123, this.second);
        assertThat(this.logs.list).filteredOn(log -> log.getFormattedMessage().startsWith("Retrying delivery"))
                .hasSize(2).allSatisfy(log -> assertThat(log.getMDCPropertyMap()).containsEntry("event.id", "retry-event"));
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void retriesTransientDiscordErrorsAtMostTwiceAndLogsFinalFailure(int status) {
        ClientException failure = failure(status, null);
        doReturn(Mono.error(failure)).when(this.service).directSendMessagePassErrors(123, this.first);
        StepVerifier.create(this.service.sendMessagesInOrder(123, List.of(this.first)))
                .then(() -> this.scheduler.advanceTimeBy(Duration.ofSeconds(3)))
                .expectErrorMatches(error -> error == failure).verify();
        verify(this.service, times(3)).directSendMessagePassErrors(123, this.first);
        assertThat(this.logs.list).anySatisfy(log ->
                assertThat(log.getFormattedMessage()).contains("Bot delivery failed", "123", "HTTP " + status));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 413})
    void doesNotRetryPermanentDiscordErrors(int status) {
        ClientException failure = failure(status, null);
        doReturn(Mono.error(failure)).when(this.service).directSendMessagePassErrors(123, this.first);
        StepVerifier.create(this.service.sendMessagesInOrder(123, List.of(this.first))).expectError(ClientException.class).verify();
        this.scheduler.advanceTimeBy(Duration.ofMinutes(10));
        verify(this.service).directSendMessagePassErrors(123, this.first);
    }

    @Test
    void cancellationDuringRetryDelayDoesNotSendAgain() {
        doReturn(Mono.error(new IOException("network"))).when(this.service).directSendMessagePassErrors(123, this.first);
        var delivery = this.service.sendMessagesInOrder(123, List.of(this.first)).subscribe();
        verify(this.service).directSendMessagePassErrors(123, this.first);
        delivery.dispose();
        this.scheduler.advanceTimeBy(Duration.ofMinutes(1));
        verify(this.service).directSendMessagePassErrors(123, this.first);
    }

    static Stream<Throwable> networkFailures() {
        return Stream.of(new IOException("connection reset"), new TimeoutException(),
                io.netty.handler.timeout.ReadTimeoutException.INSTANCE, new CompletionException(new IOException("connection closed")));
    }

    @ParameterizedTest
    @MethodSource("networkFailures")
    void retriesTransportAndTimeoutFailures(Throwable failure) {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> attempts.incrementAndGet() == 1 ? Mono.error(failure) : Mono.empty())
                .when(this.service).directSendMessagePassErrors(123, this.first);
        StepVerifier.create(this.service.sendMessagesInOrder(123, List.of(this.first)))
                .then(() -> this.scheduler.advanceTimeBy(Duration.ofSeconds(1))).verifyComplete();
        assertThat(attempts.get()).isEqualTo(2);
    }

    static Stream<Throwable> permanentFailures() {
        return Stream.of(new IllegalArgumentException("bad payload"), new CancellationException(), new InterruptedException());
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void doesNotRetryDeterministicFailuresOrInterruption(Throwable failure) {
        doReturn(Mono.error(failure)).when(this.service).directSendMessagePassErrors(123, this.first);
        StepVerifier.create(this.service.sendMessagesInOrder(123, List.of(this.first))).expectErrorMatches(error -> error == failure).verify();
        this.scheduler.advanceTimeBy(Duration.ofMinutes(1));
        verify(this.service).directSendMessagePassErrors(123, this.first);
    }

    @Test
    void respectsRetryAfterAndDoesNotRetryBeforeIt() {
        AtomicInteger attempts = new AtomicInteger();
        ClientException failure = failure(429, "4.001");
        doAnswer(invocation -> attempts.incrementAndGet() == 1 ? Mono.error(failure) : Mono.empty())
                .when(this.service).directSendMessagePassErrors(123, this.first);
        StepVerifier.create(this.service.sendMessagesInOrder(123, List.of(this.first)))
                .then(() -> this.scheduler.advanceTimeBy(Duration.ofSeconds(4)))
                .then(() -> assertThat(attempts.get()).isEqualTo(1))
                .then(() -> this.scheduler.advanceTimeBy(Duration.ofMillis(1)))
                .verifyComplete();
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void failureInOneChannelDoesNotPreventOtherChannelsAndCleanupStillRuns() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(this.cleanup).execute(any());
        I18n i18n = new I18n("ja_jp");
        var partition = new DeliveryPartition(Map.of(), Map.of(
                123L, new DeliveryTarget(123, "ja_jp", null), 456L, new DeliveryTarget(456, "ja_jp", null)));
        this.service = spy(new EEWService(null, null, this.registry, null, i18n, null,
                this.cleanup, null, new ConfigV2(), this.scheduler));
        doReturn(Mono.error(failure(403, null))).when(this.service).directSendMessagePassErrors(eq(123L), any());
        doReturn(Mono.empty()).when(this.service).directSendMessagePassErrors(eq(456L), any());
        Entity entity = mock(Entity.class);
        when(entity.createMessages(anyString(), any())).thenReturn(List.of(this.first));
        when(entity.createWebhooks(anyString(), any())).thenReturn(List.of());
        this.service.sendMessage(partition, entity);
        verify(this.service).directSendMessagePassErrors(456, this.first);
        verify(this.registry).remove(123);
        verify(this.registry).save();
    }

    static ClientException failure(int status, String retryAfter) {
        ClientException failure = mock(ClientException.class);
        when(failure.getStatus()).thenReturn(HttpResponseStatus.valueOf(status));
        var headers = new DefaultHttpHeaders();
        if (retryAfter != null)
            headers.set("Retry-After", retryAfter);
        when(failure.getHeaders()).thenReturn(headers);
        return failure;
    }
}
