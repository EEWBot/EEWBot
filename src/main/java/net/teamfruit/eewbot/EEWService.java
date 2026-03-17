package net.teamfruit.eewbot;

import com.google.gson.reflect.TypeToken;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.Entity;
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
import org.apache.hc.client5.http.async.methods.*;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
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
    private final MinimalHttpAsyncClient asyncHttpClient;
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
        this.webhookSenderHeader = config.getWebhookSender().getCustomHeader().split(":");

        int poolingMax = config.getAdvanced().getPoolingMax();
        int poolingMaxPerRoute = config.getAdvanced().getPoolingMaxPerRoute();

        PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(poolingMax)
                .setMaxConnPerRoute(poolingMaxPerRoute)
                .build();
        this.asyncHttpClient = HttpAsyncClients.createMinimal(
                H2Config.DEFAULT,
                Http1Config.DEFAULT,
                IOReactorConfig.DEFAULT,
                connectionManager);
        this.asyncHttpClient.start();

        if (StringUtils.isNotEmpty(config.getWebhookSender().getAddress()))
            this.webhookSenderAddress = URI.create(config.getWebhookSender().getAddress());
        else
            this.webhookSenderAddress = null;
    }

    public void sendMessage(final ChannelFilter filter, final Entity entity) {
        Map<String, MessageCreateSpec> msgByLang = new HashMap<>();
        this.i18n.getLanguages().keySet().forEach(lang -> msgByLang.put(lang, entity.createMessage(lang, this.embedContext)));

        DeliveryPartition partition = this.deliveryRegistry.getDeliveryChannels(filter);

        List<DiscordWebhookRequest> webhookRequests = new ArrayList<>();
        this.i18n.getLanguages().keySet().forEach(lang -> {
            DiscordWebhook webhook = entity.createWebhook(lang, this.embedContext);
            webhook.avatar_url = this.avatarUrl;
            webhookRequests.add(new DiscordWebhookRequest(lang, webhook));
        });

        Map<Long, DeliveryTarget> webhookChannels = partition.webhook();
        if (!webhookChannels.isEmpty()) {
            if (this.webhookSenderAddress == null) {
                Log.logger.info("Sending webhook message to {} channels", webhookChannels.size());
                sendWebhook(webhookRequests, webhookChannels, (id, target) -> directSendMessagePassErrors(id, msgByLang.get(target.lang())).subscribe());
            } else {
                Log.logger.info("Sending webhook message to {} channels via webhook sender", webhookChannels.size());
                sendWebhookSender(webhookRequests, webhookChannels, channels ->
                        sendWebhook(webhookRequests, channels, (id, target) -> directSendMessagePassErrors(id, msgByLang.get(target.lang())).subscribe()));
            }
        }

        Map<Long, DeliveryTarget> channels = partition.direct();
        if (!channels.isEmpty()) {
            Log.logger.info("Sending message to {} channels", channels.size());
            sendMessageD4J(channels, msgByLang);
        }
    }

    private void submitCleanup(Runnable task) {
        Runnable wrapped = MdcUtil.wrapWithMdc(task);
        try {
            this.executor.execute(wrapped);
        } catch (RejectedExecutionException e) {
            Log.logger.debug("Cleanup task skipped (executor shut down)");
        }
    }

    private void sendMessageD4J(Map<Long, DeliveryTarget> channels, Map<String, MessageCreateSpec> msgByLang) {
        List<Long> erroredChannels = new ArrayList<>();
        Flux.merge(channels.entrySet().stream()
                        .map(entry -> directSendMessagePassErrors(entry.getKey(), msgByLang.get(entry.getValue().lang()))
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

    private void sendWebhook(List<DiscordWebhookRequest> webhookRequests, Map<Long, DeliveryTarget> webhookChannels, BiFunction<Long, DeliveryTarget, Disposable> onError) {
        HttpHost target = new HttpHost("https", "discord.com");
        Map<String, SimpleHttpRequest> cacheReq = new HashMap<>();
        webhookRequests.forEach(webhookRequest -> cacheReq.put(webhookRequest.getLang(), SimpleRequestBuilder.post()
                .setHttpHost(target)
                .addHeader("User-Agent", "EEWBot")
                .setBody(Codecs.GSON.toJson(webhookRequest.getWebhook()), ContentType.APPLICATION_JSON)
                .build()));

        try {
            final Future<AsyncClientEndpoint> leaseFuture = this.asyncHttpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(30, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(webhookChannels.size());
                Map<Long, DeliveryTarget> erroredChannels = new ConcurrentHashMap<>();
                webhookChannels.forEach((channelId, channel) -> {
                    SimpleHttpRequest request = SimpleRequestBuilder.copy(cacheReq.get(channel.lang()))
                            .setUri(channel.webhookUrl())
                            .build();
                    endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), MdcUtil.wrapWithMdc(new FutureCallback<>() {
                        @Override
                        public void completed(SimpleHttpResponse simpleHttpResponse) {
                            latch.countDown();
                            if (simpleHttpResponse.getCode() < 200 || simpleHttpResponse.getCode() >= 300) {
                                onError.apply(channelId, channel);
                            }
                            if (simpleHttpResponse.getCode() == 404) {
                                erroredChannels.put(channelId, channel);
                            }
                        }

                        @Override
                        public void failed(Exception e) {
                            latch.countDown();
                            Log.logger.info("Failed to send webhook: ChannelID={} Message={}", channelId, e.getMessage());
                            onError.apply(channelId, channel);
                        }

                        @Override
                        public void cancelled() {
                            latch.countDown();
                            Log.logger.info("Cancelled to send webhook: ChannelID={}", channelId);
                            onError.apply(channelId, channel);
                        }
                    }));
                });
                latch.await();
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
            } finally {
                endpoint.releaseAndReuse();
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.logger.error("Failed to send message");
        }
    }

    private void sendWebhookSender(List<DiscordWebhookRequest> webhookRequests, Map<Long, DeliveryTarget> webhookChannels, Consumer<Map<Long, DeliveryTarget>> onError) {
        Map<String, List<String>> targetsByLang = webhookChannels.values().stream()
                .collect(Collectors.groupingBy(
                        DeliveryTarget::lang,
                        Collectors.mapping(DeliveryTarget::webhookUrl, Collectors.toList())
                ));

        List<WebhookSenderRequest> senderRequests = webhookRequests.stream()
                .peek(webhookRequest -> webhookRequest.getTargets()
                        .addAll(targetsByLang.getOrDefault(webhookRequest.getLang(), Collections.emptyList())))
                .filter(webhookRequest -> !webhookRequest.getTargets().isEmpty())
                .map(WebhookSenderRequest::from)
                .collect(Collectors.toList());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URIBuilder(this.webhookSenderAddress).setPath("/api/send").build())
                    .header("User-Agent", "EEWBot")
                    .header("Content-Type", "application/json")
                    .headers(this.webhookSenderHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(Codecs.GSON.toJson(senderRequests)))
                    .build();

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Log.logger.error("Failed to send message to webhook sender: {} {}", response.statusCode(), response.body());
                onError.accept(webhookChannels);
                return;
            }
            Log.logger.info("Sent message to webhook sender: {}", response.body());
        } catch (InterruptedException e) {
            Log.logger.error("Interrupted while sending messages to webhook sender", e);
        } catch (URISyntaxException e) {
            Log.logger.error("Invalid webhook sender send URI", e);
        } catch (Exception e) {
            Log.logger.error("Failed to send message to webhook sender", e);
        }
    }

    public int sendWebhookSenderSingle(WebhookSenderRequest senderRequest) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URIBuilder(this.webhookSenderAddress).setPath("/api/send").build())
                .header("User-Agent", "EEWBot")
                .header("Content-Type", "application/json")
                .headers(this.webhookSenderHeader)
                .POST(HttpRequest.BodyPublishers.ofString(Codecs.GSON.toJson(List.of(senderRequest))))
                .build();
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
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(new URIBuilder(this.webhookSenderAddress).setPath("/api/notfounds").build())
                    .header("User-Agent", "EEWBot")
                    .headers(this.webhookSenderHeader)
                    .build();
            HttpResponse<String> getResponse = this.httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            if (getResponse.statusCode() != 200) {
                Log.logger.error("Failed to fetch not founds from webhook sender: " + getResponse.statusCode() + " " + getResponse.body());
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
        } catch (URISyntaxException e) {
            Log.logger.error("Invalid webhook sender not founds URI", e);
        } catch (Exception e) {
            Log.logger.error("Failed to fetch not founds from webhook sender", e);
        }
    }
}
