package net.teamfruit.eewbot;

import com.google.gson.reflect.TypeToken;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.discord.DiscordLimits;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.discord.DiscordWebhookRequest;
import net.teamfruit.eewbot.entity.webhooksender.WebhookSenderRequest;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EEWService {

    private final GatewayDiscordClient gateway;
    private final String avatarUrl;
    private final I18n i18n;
    private final ScheduledExecutorService executor;
    private final DestinationDeliveryRegistry deliveryRegistry;
    private final DestinationAdminRegistry adminRegistry;
    private final EmbedContext embedContext;
    private final HttpClient httpClient;
    private final URI webhookSenderAddress;
    private final String[] webhookSenderHeader;

    public EEWService(
            GatewayDiscordClient gateway,
            DestinationDeliveryRegistry deliveryRegistry,
            DestinationAdminRegistry adminRegistry,
            String avatarUrl,
            I18n i18n,
            EmbedContext embedContext,
            ScheduledExecutorService executor,
            HttpClient httpClient,
            ConfigV2 config
    ) {
        this.gateway = gateway;
        this.deliveryRegistry = deliveryRegistry;
        this.adminRegistry = adminRegistry;
        this.avatarUrl = avatarUrl;
        this.i18n = i18n;
        this.embedContext = embedContext;
        this.executor = executor;
        this.httpClient = httpClient;
        this.webhookSenderHeader = parseWebhookSenderHeader(config.getWebhookSender().getCustomHeader());

        if (StringUtils.isNotEmpty(config.getWebhookSender().getAddress()))
            this.webhookSenderAddress = URI.create(config.getWebhookSender().getAddress());
        else
            this.webhookSenderAddress = null;
    }

    public void sendMessage(final ChannelFilter filter, final Entity entity) {
        Map<String, List<MessageCreateSpec>> msgByLang = new HashMap<>();
        this.i18n.getLanguages().keySet().forEach(lang -> msgByLang.put(lang, entity.createMessages(lang, this.embedContext)));

        DeliveryPartition partition = this.deliveryRegistry.getDeliveryChannels(filter);

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
            if (this.webhookSenderAddress == null) {
                Log.logger.info("Sending webhook message to {} channels", webhookChannels.size());
                sendWebhook(webhookChunks, webhookChannels, (id, target) -> sendMessagesInOrder(id, msgByLang.get(target.lang())).subscribe());
            } else {
                Log.logger.info("Sending webhook message to {} channels via webhook sender", webhookChannels.size());
                sendWebhookSender(webhookChunks, webhookChannels, channels ->
                        sendWebhook(webhookChunks, channels, (id, target) -> sendMessagesInOrder(id, msgByLang.get(target.lang())).subscribe()));
            }
        }

        Map<Long, DeliveryTarget> channels = partition.direct();
        if (!channels.isEmpty()) {
            Log.logger.info("Sending message to {} channels", channels.size());
            sendMessageD4J(channels, msgByLang);
        }
    }

    private Flux<Message> sendMessagesInOrder(long channelId, List<MessageCreateSpec> specs) {
        return Flux.fromIterable(specs).concatMap(spec -> directSendMessagePassErrors(channelId, spec));
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
                .parallel()
                .runOn(Schedulers.parallel())
                .doOnComplete(() -> {
                    if (!erroredChannels.isEmpty()) {
                        submitCleanup(() -> {
                            Thread.currentThread().setName("eewbot-channel-unregister-thread");

                            erroredChannels.forEach(channelId -> {
                                this.adminRegistry.remove(channelId);
                                Log.logger.info("Channel {} permission is missing or has been deleted, unregister", channelId);
                            });
                            try {
                                this.adminRegistry.save();
                            } catch (IOException e) {
                                Log.logger.error("Failed to save channels", e);
                            }
                        });
                    }
                })
                .subscribe();
    }

    private void sendWebhook(List<List<DiscordWebhookRequest>> webhookChunks, Map<Long, DeliveryTarget> webhookChannels, BiFunction<Long, DeliveryTarget, Disposable> onError) {
        List<Map<String, String>> jsonByChunkAndLang = new ArrayList<>(webhookChunks.size());
        for (List<DiscordWebhookRequest> chunk : webhookChunks) {
            Map<String, String> jsonByLang = new HashMap<>();
            chunk.forEach(req -> {
                String json = Codecs.GSON.toJson(req.getWebhook());
                int bytes = json.getBytes(StandardCharsets.UTF_8).length;
                if (bytes > DiscordLimits.MAX_REQUEST_BYTES)
                    Log.logger.error("Webhook payload for language {} is {} bytes, over the {} byte budget; Discord may answer 500",
                            req.getLang(), bytes, DiscordLimits.MAX_REQUEST_BYTES);
                jsonByLang.put(req.getLang(), json);
            });
            jsonByChunkAndLang.add(jsonByLang);
        }

        Map<Long, DeliveryTarget> erroredChannels = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<String, String> mdcCtx = MDC.getCopyOfContextMap();

        webhookChannels.forEach((channelId, channel) -> {
            CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
            for (Map<String, String> jsonByLang : jsonByChunkAndLang) {
                String body = jsonByLang.get(channel.lang());
                if (body == null)
                    continue;
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
                                        onError.apply(channelId, channel);
                                        return false;
                                    }
                                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                                        onError.apply(channelId, channel);
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

                erroredChannels.forEach((channelId, channel) -> {
                    Log.logger.info("Webhook for channel {} is deleted, unregister", channelId);
                    this.adminRegistry.setWebhook(channelId, null);
                });
                try {
                    this.adminRegistry.save();
                } catch (IOException e) {
                    Log.logger.error("Failed to save channels", e);
                }
            });
        }
    }

    private void sendWebhookSender(List<List<DiscordWebhookRequest>> webhookChunks, Map<Long, DeliveryTarget> webhookChannels, Consumer<Map<Long, DeliveryTarget>> onError) {
        Map<String, List<String>> targetsByLang = webhookChannels.values().stream()
                .collect(Collectors.groupingBy(
                        DeliveryTarget::lang,
                        Collectors.mapping(DeliveryTarget::webhookUrl, Collectors.toList())
                ));

        for (List<DiscordWebhookRequest> chunk : webhookChunks) {
            List<WebhookSenderRequest> senderRequests = chunk.stream()
                    .peek(webhookRequest -> webhookRequest.getTargets()
                            .addAll(targetsByLang.getOrDefault(webhookRequest.getLang(), Collections.emptyList())))
                    .filter(webhookRequest -> !webhookRequest.getTargets().isEmpty())
                    .map(WebhookSenderRequest::from)
                    .collect(Collectors.toList());

            if (senderRequests.isEmpty())
                continue;

            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(replacePathPreservingQuery(this.webhookSenderAddress, "/api/send"))
                        .header("User-Agent", "EEWBot")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Codecs.GSON.toJson(senderRequests)));
                applyWebhookSenderHeader(requestBuilder);
                HttpRequest request = requestBuilder.build();

                HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    Log.logger.error("Failed to send message to webhook sender: {} {}", response.statusCode(), LogSanitizer.maskDiscordWebhookUrlsInText(response.body()));
                    onError.accept(webhookChannels);
                    return;
                }
                Log.logger.info("Sent message to webhook sender: {}", LogSanitizer.maskDiscordWebhookUrlsInText(response.body()));
            } catch (InterruptedException e) {
                Log.logger.error("Interrupted while sending messages to webhook sender", e);
                onError.accept(webhookChannels);
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Log.logger.error("Failed to send message to webhook sender", e);
                onError.accept(webhookChannels);
                return;
            }
        }
    }

    public int sendWebhookSenderSingle(WebhookSenderRequest senderRequest) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(replacePathPreservingQuery(this.webhookSenderAddress, "/api/send"))
                .header("User-Agent", "EEWBot")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Codecs.GSON.toJson(List.of(senderRequest))));
        applyWebhookSenderHeader(requestBuilder);
        HttpRequest request = requestBuilder.build();
        HttpResponse<Void> response = this.httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    public Mono<Message> directSendMessagePassErrors(long channelId, MessageCreateSpec spec) {
        return Mono.defer(() -> this.gateway.getRestClient().getChannelService()
                        .createMessage(channelId, spec.asRequest()))
                .map(data -> new Message(this.gateway, data));
    }

    public void handleWebhookSenderNotFounds() {
        Thread.currentThread().setName("eewbot-webhook-sender-handle-not-found-thread");

        try {
            HttpRequest.Builder getRequestBuilder = HttpRequest.newBuilder()
                    .GET()
                    .uri(replacePathPreservingQuery(this.webhookSenderAddress, "/api/notfounds"))
                    .header("User-Agent", "EEWBot");
            applyWebhookSenderHeader(getRequestBuilder);
            HttpRequest getRequest = getRequestBuilder.build();
            HttpResponse<String> getResponse = this.httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            if (getResponse.statusCode() != 200) {
                Log.logger.error("Failed to fetch not founds from webhook sender: " + getResponse.statusCode() + " " + LogSanitizer.maskDiscordWebhookUrlsInText(getResponse.body()));
                return;
            }

            List<String> notFoundList = Codecs.GSON.fromJson(getResponse.body(), new TypeToken<List<String>>() {
            }.getType());
            if (notFoundList.isEmpty()) {
                return;
            }

            int cleared = this.adminRegistry.clearWebhookByUrls(notFoundList);
            if (cleared > 0) {
                Log.logger.info("Cleared {} channel webhook(s) for {} not-found URL(s)", cleared, notFoundList.size());
            }
        } catch (InterruptedException e) {
            Log.logger.error("Interrupted while fetching not founds from webhook sender", e);
        } catch (Exception e) {
            Log.logger.error("Failed to fetch not founds from webhook sender", e);
        }
    }

    private static URI replacePathPreservingQuery(URI base, String newPath) {
        try {
            return new URI(base.getScheme(), base.getRawAuthority(), newPath, base.getRawQuery(), null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + base + " with path " + newPath, e);
        }
    }

    private void applyWebhookSenderHeader(HttpRequest.Builder builder) {
        if (this.webhookSenderHeader.length > 0) {
            builder.headers(this.webhookSenderHeader);
        }
    }

    private static String[] parseWebhookSenderHeader(String customHeader) {
        if (StringUtils.isBlank(customHeader)) {
            return new String[0];
        }

        String[] header = customHeader.split(":", 2);
        if (header.length != 2 || StringUtils.isBlank(header[0]) || StringUtils.isBlank(header[1])) {
            Log.logger.warn("Ignoring invalid webhook sender custom header");
            return new String[0];
        }
        return new String[]{header[0].trim(), header[1].trim()};
    }
}
