package net.teamfruit.eewbot;

import com.google.gson.reflect.TypeToken;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.discord.EmbedWebhookCost;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.discord.DiscordWebhookRequest;
import net.teamfruit.eewbot.entity.webhooksender.WebhookSenderRequest;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationCleanup;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class EEWService {

    @FunctionalInterface
    private interface WebhookFallback {
        void onFailed(long channelId, DeliveryTarget target, int fromChunkIndex);
    }

    @FunctionalInterface
    private interface SenderFallback {
        void onFailed(Map<Long, DeliveryTarget> channels, int fromChunkIndex);
    }

    private final GatewayDiscordClient gateway;
    private final String avatarUrl;
    private final I18n i18n;
    private final ScheduledExecutorService executor;
    private final DestinationDeliveryRegistry deliveryRegistry;
    private final DestinationCleanup destinationCleanup;
    private final EmbedContext embedContext;
    private final HttpClient httpClient;
    private final WebhookSenderClient webhookSenderClient;
    private final Scheduler deliveryScheduler;

    public EEWService(
            GatewayDiscordClient gateway,
            DestinationDeliveryRegistry deliveryRegistry,
            DestinationCleanup destinationCleanup,
            String avatarUrl,
            I18n i18n,
            EmbedContext embedContext,
            ScheduledExecutorService executor,
            HttpClient httpClient,
            ConfigV2 config
    ) {
        this(gateway, deliveryRegistry, destinationCleanup, avatarUrl, i18n, embedContext, executor, httpClient, config,
                Schedulers.parallel());
    }

    EEWService(GatewayDiscordClient gateway, DestinationDeliveryRegistry deliveryRegistry,
               DestinationCleanup destinationCleanup, String avatarUrl, I18n i18n, EmbedContext embedContext,
               ScheduledExecutorService executor, HttpClient httpClient, ConfigV2 config, Scheduler deliveryScheduler) {
        this.gateway = gateway;
        this.deliveryRegistry = deliveryRegistry;
        this.destinationCleanup = destinationCleanup;
        this.avatarUrl = avatarUrl;
        this.i18n = i18n;
        this.embedContext = embedContext;
        this.executor = executor;
        this.httpClient = httpClient;
        this.deliveryScheduler = deliveryScheduler;
        this.webhookSenderClient = new WebhookSenderClient(httpClient, config.getWebhookSender());
    }

    public void sendMessage(final ChannelFilter filter, final Entity entity) {
        sendMessage(this.deliveryRegistry.getDeliveryChannels(filter), entity);
    }

    public void sendMessage(final List<ChannelFilter> filters, final Entity entity) {
        sendMessage(this.deliveryRegistry.getDeliveryChannelsMatchingAny(filters), entity);
    }

    public void sendMessage(final DeliveryPartition partition, final Entity entity) {
        Map<String, List<MessageCreateSpec>> msgByLang = new HashMap<>();
        this.i18n.getLanguages().keySet().forEach(lang -> msgByLang.put(lang, entity.createMessages(lang, this.embedContext)));

        List<List<DiscordWebhookRequest>> webhookChunks = new ArrayList<>();
        this.i18n.getLanguages().keySet().forEach(lang -> {
            List<DiscordWebhook> webhooks = entity.createWebhooks(lang, this.embedContext);
            for (int i = 0; i < webhooks.size(); i++) {
                DiscordWebhook webhook = webhooks.get(i);
                webhook.avatar_url = this.avatarUrl;
                while (webhookChunks.size() <= i)
                    webhookChunks.add(new ArrayList<>());
                webhookChunks.get(i).add(new DiscordWebhookRequest(lang, webhook));
            }
        });

        if (webhookChunks.size() > 1)
            Log.logger.info("Entity does not fit in one message, splitting into {} messages", webhookChunks.size());

        Map<Long, DeliveryTarget> webhookChannels = partition.webhook();
        if (!webhookChannels.isEmpty()) {
            WebhookFallback botFallback = (id, target, fromChunkIndex) ->
                    sendMessagesInOrder(id, remainingMessages(msgByLang.get(target.lang()), fromChunkIndex))
                            .onErrorResume(error -> Mono.empty()).subscribe();

            if (!this.webhookSenderClient.isConfigured()) {
                Log.logger.info("Sending webhook message to {} channels", webhookChannels.size());
                sendWebhook(webhookChunks, 0, webhookChannels, botFallback);
            } else {
                Log.logger.info("Sending webhook message to {} channels via webhook sender", webhookChannels.size());
                sendWebhookSender(webhookChunks, webhookChannels, (channels, fromChunkIndex) ->
                        sendWebhook(webhookChunks, fromChunkIndex, channels, botFallback));
            }
        }

        Map<Long, DeliveryTarget> channels = partition.direct();
        if (!channels.isEmpty()) {
            Log.logger.info("Sending message to {} channels", channels.size());
            sendMessageD4J(channels, msgByLang);
        }
    }

    Flux<Message> sendMessagesInOrder(long channelId, List<MessageCreateSpec> specs) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        var retry = DeliveryRetry.policy(this.deliveryScheduler, "Discord channel " + channelId);
        return Flux.fromIterable(specs).concatMap(spec -> Mono.defer(() -> directSendMessagePassErrors(channelId, spec))
                .retryWhen(retry)
                .doOnError(error -> MdcUtil.wrapWithMdc(() -> Log.logger.error(
                        "Bot delivery failed: channelId={}, error={}", channelId, DeliveryRetry.describe(error)), context).run()));
    }

    private static List<MessageCreateSpec> remainingMessages(List<MessageCreateSpec> specs, int fromIndex) {
        if (specs == null || fromIndex >= specs.size())
            return Collections.emptyList();
        return specs.subList(fromIndex, specs.size());
    }

    private void submitCleanup(Runnable task) {
        Runnable wrapped = MdcUtil.wrapWithMdc(task);
        try {
            this.executor.execute(wrapped);
        } catch (RejectedExecutionException e) {
            Log.logger.debug("Cleanup task skipped (executor shut down)");
        }
    }

    private void sendMessageD4J(Map<Long, DeliveryTarget> channels, Map<String, List<MessageCreateSpec>> msgByLang) {
        List<Long> erroredChannels = new ArrayList<>();
        Flux.merge(channels.entrySet().stream()
                        .map(entry -> sendMessagesInOrder(entry.getKey(), msgByLang.get(entry.getValue().lang()))
                                .doOnError(ClientException.class, err -> {
                                    if ((err.getStatus() == HttpResponseStatus.NOT_FOUND || err.getStatus() == HttpResponseStatus.FORBIDDEN)) {
                                        synchronized (erroredChannels) {
                                            erroredChannels.add(entry.getKey());
                                        }
                                    }
                                })
                                .onErrorResume(e -> Mono.empty()))
                        .collect(Collectors.toList()))
                .doOnComplete(() -> {
                    if (!erroredChannels.isEmpty()) {
                        submitCleanup(() -> {
                            Thread.currentThread().setName("eewbot-channel-unregister-thread");

                            erroredChannels.forEach(channelId ->
                                    Log.logger.info("Channel {} permission is missing or has been deleted; requesting cleanup", channelId));
                            try {
                                this.destinationCleanup.destinationsUnavailable(erroredChannels);
                            } catch (IOException e) {
                                Log.logger.error("Failed to save channels", e);
                            }
                        });
                    }
                })
                .subscribe(ignored -> { }, error -> Log.logger.error("Bot delivery fan-out failed: {}", DeliveryRetry.describe(error)));
    }

    private void sendWebhook(List<List<DiscordWebhookRequest>> webhookChunks, int fromChunkIndex, Map<Long, DeliveryTarget> webhookChannels, WebhookFallback onError) {
        List<Map<String, String>> jsonByChunkAndLang = new ArrayList<>(webhookChunks.size() - fromChunkIndex);
        for (int i = fromChunkIndex; i < webhookChunks.size(); i++) {
            Map<String, String> jsonByLang = new HashMap<>();
            webhookChunks.get(i).forEach(req -> {
                String json = Codecs.GSON.toJson(req.getWebhook());
                int cost = EmbedWebhookCost.estimate(req.getWebhook());
                if (cost > EmbedWebhookCost.OBSERVED_BUDGET)
                    Log.logger.error("Webhook payload for language {} has effective cost {}, over the {} budget",
                            req.getLang(), cost, EmbedWebhookCost.OBSERVED_BUDGET);
                jsonByLang.put(req.getLang(), json);
            });
            jsonByChunkAndLang.add(jsonByLang);
        }

        Map<Long, DeliveryTarget> erroredChannels = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<String, String> mdcCtx = MDC.getCopyOfContextMap();

        webhookChannels.forEach((channelId, channel) -> {
            CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
            for (int i = 0; i < jsonByChunkAndLang.size(); i++) {
                String body = jsonByChunkAndLang.get(i).get(channel.lang());
                if (body == null)
                    continue;
                final int chunkIndex = fromChunkIndex + i;
                chain = chain.thenCompose(keepGoing -> {
                    if (!keepGoing)
                        return CompletableFuture.completedFuture(false);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(channel.webhookUrl()))
                            .header("User-Agent", "EEWBot")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                            .handle((response, ex) -> {
                                Map<String, String> prev = MDC.getCopyOfContextMap();
                                if (mdcCtx != null) MDC.setContextMap(mdcCtx);
                                else MDC.clear();
                                try {
                                    if (ex != null) {
                                        Log.logger.info("Failed to send webhook: ChannelID={} Message={}", channelId, LogSanitizer.safeExceptionMessage(ex));
                                        onError.onFailed(channelId, channel, chunkIndex);
                                        return false;
                                    }
                                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                                        onError.onFailed(channelId, channel, chunkIndex);
                                        if (response.statusCode() == 404) {
                                            erroredChannels.put(channelId, channel);
                                        }
                                        return false;
                                    }
                                    return true;
                                } finally {
                                    if (prev != null) MDC.setContextMap(prev);
                                    else MDC.clear();
                                }
                            });
                });
            }
            futures.add(chain.thenAccept(ignored -> {
            }));
        });

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException e) {
            Log.logger.warn("Webhook fan-out interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Log.logger.error("Failed to send message", e);
        }

        if (!erroredChannels.isEmpty()) {
            submitCleanup(() -> {
                Thread.currentThread().setName("eewbot-channel-unregister-thread");

                erroredChannels.forEach((channelId, channel) ->
                        Log.logger.info("Webhook for channel {} returned not found; requesting cleanup", channelId));
                try {
                    this.destinationCleanup.webhooksUnavailable(erroredChannels);
                } catch (IOException e) {
                    Log.logger.error("Failed to save channels", e);
                }
            });
        }
    }

    private void sendWebhookSender(List<List<DiscordWebhookRequest>> webhookChunks, Map<Long, DeliveryTarget> webhookChannels, SenderFallback onError) {
        Map<String, List<String>> targetsByLang = webhookChannels.values().stream()
                .collect(Collectors.groupingBy(
                        DeliveryTarget::lang,
                        Collectors.mapping(DeliveryTarget::webhookUrl, Collectors.toList())
                ));

        for (int chunkIndex = 0; chunkIndex < webhookChunks.size(); chunkIndex++) {
            List<WebhookSenderRequest> senderRequests = webhookChunks.get(chunkIndex).stream()
                    .peek(webhookRequest -> webhookRequest.getTargets()
                            .addAll(targetsByLang.getOrDefault(webhookRequest.getLang(), Collections.emptyList())))
                    .filter(webhookRequest -> !webhookRequest.getTargets().isEmpty())
                    .map(WebhookSenderRequest::from)
                    .collect(Collectors.toList());

            if (senderRequests.isEmpty())
                continue;

            try {
                HttpResponse<String> response = this.webhookSenderClient.send(senderRequests);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    Log.logger.error("Failed to send message to webhook sender: {} {}", response.statusCode(), LogSanitizer.maskDiscordWebhookUrlsInText(response.body()));
                    onError.onFailed(webhookChannels, chunkIndex);
                    return;
                }
                Log.logger.info("Sent message to webhook sender: {}", LogSanitizer.maskDiscordWebhookUrlsInText(response.body()));
            } catch (InterruptedException e) {
                Log.logger.error("Interrupted while sending messages to webhook sender", e);
                onError.onFailed(webhookChannels, chunkIndex);
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Log.logger.error("Failed to send message to webhook sender", e);
                onError.onFailed(webhookChannels, chunkIndex);
                return;
            }
        }
    }

    Mono<Message> directSendMessagePassErrors(long channelId, MessageCreateSpec spec) {
        return Mono.defer(() -> this.gateway.getRestClient().getChannelService()
                        .createMessage(channelId, spec.asRequest()))
                .map(data -> new Message(this.gateway, data));
    }

    public void handleWebhookSenderNotFounds() {
        Thread.currentThread().setName("eewbot-webhook-sender-handle-not-found-thread");

        try {
            HttpResponse<String> getResponse = this.webhookSenderClient.getNotFounds();
            if (getResponse.statusCode() != 200) {
                Log.logger.error("Failed to fetch not founds from webhook sender: " + getResponse.statusCode() + " " + LogSanitizer.maskDiscordWebhookUrlsInText(getResponse.body()));
                return;
            }

            List<String> notFoundList = Codecs.GSON.fromJson(getResponse.body(), new TypeToken<List<String>>() {
            }.getType());
            if (notFoundList.isEmpty()) {
                return;
            }

            int cleared = this.destinationCleanup.webhookUrlsUnavailable(notFoundList);
            if (cleared > 0) {
                Log.logger.info("Cleared {} channel webhook(s) for {} not-found URL(s)", cleared, notFoundList.size());
            }
        } catch (InterruptedException e) {
            Log.logger.error("Interrupted while fetching not founds from webhook sender", e);
        } catch (Exception e) {
            Log.logger.error("Failed to fetch not founds from webhook sender", e);
        }
    }

}
