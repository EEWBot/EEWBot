package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.ExternalWebhookRequest;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.slf4j.MDC;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ExternalWebhookService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final List<String> webhookUrls;
    private final Scheduler scheduler;
    private final Disposable.Composite deliveries = Disposables.composite();

    public ExternalWebhookService(ConfigV2 config, HttpClient httpClient) {
        this(config, httpClient, Schedulers.newSingle("eewbot-external-webhook-thread"));
    }

    ExternalWebhookService(ConfigV2 config, HttpClient httpClient, Scheduler scheduler) {
        this.httpClient = httpClient;
        this.webhookUrls = config.getExternalWebhook().getUrls();
        this.scheduler = scheduler;
    }

    public void sendExternalWebhook(ExternalData externalData) {
        if (this.webhookUrls.isEmpty())
            return;
        if (this.deliveries.isDisposed()) {
            Log.logger.warn("External webhook dispatch rejected: service shut down");
            return;
        }

        final String jsonBody;
        try {
            // Serialize once so retry attempts carry exactly the same payload and timestamp.
            Object dto = externalData.toExternalDto();
            String rawData = externalData.getRawData();
            ExternalWebhookRequest request = new ExternalWebhookRequest(
                    externalData.getDataType(), Instant.now().toEpochMilli(),
                    rawData != null ? rawData : "", dto);
            jsonBody = Codecs.GSON.toJson(request);
            Log.logger.debug("External webhook JSON content: {}", jsonBody);
        } catch (Exception error) {
            Log.logger.error("Failed to serialize external data: {}", DeliveryRetry.describe(error));
            return;
        }

        Map<String, String> context = MDC.getCopyOfContextMap();
        for (String url : this.webhookUrls) {
            String destination = LogSanitizer.maskUrl(url);
            Disposable.Swap delivery = Disposables.swap();
            if (!this.deliveries.add(delivery)) {
                Log.logger.warn("External webhook dispatch rejected: service shut down");
                return;
            }
            delivery.update(Mono.defer(() -> {
                        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                                .timeout(REQUEST_TIMEOUT)
                                .header("Content-Type", "application/json")
                                .header("User-Agent", "EEWBot")
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
                        return Mono.fromFuture(this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()));
                    })
                    // fromFuture propagates cancellation to the HTTP exchange, including body stalls.
                    .timeout(REQUEST_TIMEOUT, this.scheduler)
                    .flatMap(response -> response.statusCode() >= 200 && response.statusCode() < 300
                            ? Mono.just(response)
                            : Mono.error(new DeliveryRetry.HttpFailure(response.statusCode(),
                                    response.headers().firstValue("Retry-After").orElse(null))))
                    .retryWhen(DeliveryRetry.policy(this.scheduler, "external webhook " + destination))
                    .subscribeOn(this.scheduler)
                    .doFinally(signal -> {
                        this.deliveries.remove(delivery);
                        if (signal == SignalType.CANCEL)
                            MdcUtil.wrapWithMdc(() -> Log.logger.warn(
                                    "External webhook delivery cancelled: destination={}", destination), context).run();
                    })
                    .subscribe(response -> MdcUtil.wrapWithMdc(() -> Log.logger.info(
                                    "Successfully sent external webhook to {}: status={}", destination, response.statusCode()), context).run(),
                            error -> MdcUtil.wrapWithMdc(() -> Log.logger.error(
                                    "External webhook delivery failed: destination={}, error={}", destination, DeliveryRetry.describe(error)), context).run()));
        }
    }

    public void shutdown() {
        // Stop scheduled retries and cancel outstanding HTTP exchanges; no work survives shutdown.
        this.deliveries.dispose();
        this.scheduler.dispose();
    }
}
