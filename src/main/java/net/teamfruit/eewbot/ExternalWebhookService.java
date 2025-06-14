package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.external.ExternalWebhookRequest;
import net.teamfruit.eewbot.registry.config.ConfigV2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExternalWebhookService {

    private final HttpClient httpClient;
    private final List<String> webhookUrls;
    private final ExecutorService executor;

    public ExternalWebhookService(ConfigV2 config, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.webhookUrls = config.getExternalWebhook().getUrls();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "eewbot-external-webhook-thread"));
    }

    public void sendExternalWebhook(Entity entity, String dataType) {
        if (this.webhookUrls.isEmpty()) {
            return;
        }

        ExternalWebhookRequest request = new ExternalWebhookRequest(
                dataType,
                Instant.now().toEpochMilli(),
                entity
        );

        String jsonBody = EEWBot.GSON.toJson(request);

        this.webhookUrls.forEach(url -> {
            this.executor.submit(() -> {
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "EEWBot")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

                    CompletableFuture<HttpResponse<String>> future = this.httpClient.sendAsync(
                            httpRequest, 
                            HttpResponse.BodyHandlers.ofString()
                    );

                    future.thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            Log.logger.info("Successfully sent external webhook to {}: status={}", url, response.statusCode());
                        } else {
                            Log.logger.warn("External webhook failed for {}: status={}, body={}", url, response.statusCode(), response.body());
                        }
                    }).exceptionally(throwable -> {
                        Log.logger.error("Failed to send external webhook to {}: {}", url, throwable.getMessage());
                        return null;
                    });

                } catch (Exception e) {
                    Log.logger.error("Error sending external webhook to {}: {}", url, e.getMessage());
                }
            });
        });
    }

    public void shutdown() {
        this.executor.shutdown();
    }
}