package net.teamfruit.eewbot;

import discord4j.rest.http.client.ClientException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

final class DeliveryRetry {

    private static final int MAX_RETRIES = 2;
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private DeliveryRetry() {
    }

    static Retry policy(Scheduler scheduler, String destination) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return Retry.from(signals -> signals.concatMap(signal -> {
            Throwable failure = unwrap(signal.failure());
            if (signal.totalRetries() >= MAX_RETRIES || !isTransient(failure))
                return Mono.error(failure);
            Duration delay = delay(failure, (int) signal.totalRetries(), Instant.now());
            if (delay.compareTo(MAX_DELAY) > 0) {
                MdcUtil.wrapWithMdc(() -> Log.logger.warn(
                        "Delivery retry wait exceeds limit: destination={}, delay={}; stopping retries", destination, delay), context).run();
                return Mono.error(failure);
            }
            MdcUtil.wrapWithMdc(() -> Log.logger.warn(
                    "Retrying delivery: destination={}, retry={}/{}, delay={}, error={}",
                    destination, signal.totalRetries() + 1, MAX_RETRIES, delay, describe(failure)), context).run();
            return Mono.delay(delay, scheduler);
        }));
    }

    private static boolean isTransient(Throwable error) {
        if (error instanceof ClientException client)
            return isTransientStatus(client.getStatus().code());
        if (error instanceof HttpFailure http)
            return isTransientStatus(http.status);
        return error instanceof IOException || error instanceof TimeoutException
                || error instanceof io.netty.handler.timeout.TimeoutException;
    }

    private static boolean isTransientStatus(int status) {
        return status == 408 || status == 429 || status >= 500 && status < 600;
    }

    private static Duration delay(Throwable error, int retry, Instant now) {
        Duration backoff = Duration.ofSeconds(1L << retry);
        String retryAfter = null;
        if (error instanceof ClientException client)
            retryAfter = client.getHeaders().get("Retry-After");
        else if (error instanceof HttpFailure http)
            retryAfter = http.retryAfter;
        if (retryAfter != null) {
            Duration requested;
            try {
                BigDecimal millis = new BigDecimal(retryAfter.trim()).multiply(BigDecimal.valueOf(1000));
                if (millis.compareTo(BigDecimal.valueOf(MAX_DELAY.toMillis())) > 0)
                    return MAX_DELAY.plusSeconds(1);
                requested = Duration.ofMillis(millis.setScale(0, RoundingMode.CEILING).longValueExact());
            } catch (RuntimeException invalidNumber) {
                try {
                    requested = Duration.between(now, ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                } catch (RuntimeException invalidDate) {
                    return backoff;
                }
            }
            if (requested.compareTo(backoff) > 0)
                return requested;
        }
        return backoff;
    }

    static String describe(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof ClientException client)
            return "HTTP " + client.getStatus().code();
        if (cause instanceof HttpFailure http)
            return "HTTP " + http.status;
        // Exception messages from HTTP clients can contain credential-bearing request URLs.
        return cause.getClass().getSimpleName();
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null && error.getCause() != error)
            error = error.getCause();
        return error;
    }

    static final class HttpFailure extends RuntimeException {
        private final int status;
        private final @Nullable String retryAfter;

        HttpFailure(int status, @Nullable String retryAfter) {
            super("HTTP " + status);
            this.status = status;
            this.retryAfter = retryAfter;
        }
    }
}
