package net.teamfruit.eewbot;

import com.google.gson.reflect.TypeToken;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.ChannelBase;
import net.teamfruit.eewbot.registry.ChannelFilter;
import net.teamfruit.eewbot.registry.ChannelRegistry;
import net.teamfruit.eewbot.registry.Webhook;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EEWService {

    private final GatewayDiscordClient gateway;
    private final String avatarUrl;
    private final I18n i18n;
    private final ScheduledExecutorService executor;
    private final ChannelRegistry channels;
    private final HttpClient httpClient;
    private final MinimalHttpAsyncClient asyncHttpClient;
    private final URI duplicatorAddress;

    public EEWService(EEWBot bot) {
        this.gateway = bot.getClient();
        this.channels = bot.getChannels();
        this.avatarUrl = bot.getAvatarUrl();
        this.i18n = bot.getI18n();
        this.executor = bot.getScheduledExecutor();
        this.httpClient = bot.getHttpClient();
        int poolingMax = bot.getConfig().getPoolingMax();
        int poolingMaxPerRoute = bot.getConfig().getPoolingMaxPerRoute();

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

        if (StringUtils.isNotEmpty(bot.getConfig().getDuplicatorAddress()))
            this.duplicatorAddress = URI.create(bot.getConfig().getDuplicatorAddress());
        else
            this.duplicatorAddress = null;
    }

    public void sendMessage(final ChannelFilter filter, final Entity entity, boolean highPriority) {
        Map<String, MessageCreateSpec> msgByLang = new HashMap<>();
        this.i18n.getLanguages().keySet().forEach(lang -> msgByLang.put(lang, entity.createMessage(lang)));

        Map<Boolean, Map<Long, ChannelBase>> webhookPartitioned = this.channels.getChannelsPartitionedByWebhookPresent(filter);

        Map<String, String> cacheWebhook = new HashMap<>();
        this.i18n.getLanguages().keySet().forEach(lang -> {
            DiscordWebhook webhook = entity.createWebhook(lang);
            webhook.avatar_url = this.avatarUrl;
            cacheWebhook.put(lang, webhook.json());
        });

        Map<Long, ChannelBase> webhookChannels = webhookPartitioned.get(true);
        if (!webhookChannels.isEmpty()) {
            Log.logger.info("Sending webhook message to {} channels", webhookChannels.size());
            if (this.duplicatorAddress == null) {
                sendWebhook(cacheWebhook, webhookChannels, (id, channel) -> directSendMessagePassErrors(id, msgByLang.get(channel.getLang())).subscribe());
            } else {
                sendDuplicator(highPriority, this.duplicatorAddress, cacheWebhook, webhookChannels, channels ->
                        sendWebhook(cacheWebhook, channels, (id, channel) -> directSendMessagePassErrors(id, msgByLang.get(channel.getLang())).subscribe()));
            }
        }

        Map<Long, ChannelBase> channels = webhookPartitioned.get(false);
        if (!channels.isEmpty()) {
            Log.logger.info("Sending message to {} channels", channels.size());
            sendMessageD4J(channels, msgByLang);
        }
    }

    private void sendMessageD4J(Map<Long, ChannelBase> channels, Map<String, MessageCreateSpec> msgByLang) {
        List<Long> erroredChannels = new ArrayList<>();
        Flux.merge(channels.entrySet().stream()
                        .map(entry -> directSendMessagePassErrors(entry.getKey(), msgByLang.get(entry.getValue().getLang()))
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
                        this.executor.execute(() -> {
                            Thread.currentThread().setName("eewbot-channel-unregister-thread");

                            erroredChannels.forEach(channelId -> {
                                this.channels.remove(channelId);
                                Log.logger.info("Channel {} permission is missing or has been deleted, unregister", channelId);
                            });
                            try {
                                this.channels.save();
                            } catch (IOException e) {
                                Log.logger.error("Failed to save channels", e);
                            }
                        });
                    }
                })
                .subscribe();
    }

    private void sendWebhook(Map<String, String> webhookBodyByLang, Map<Long, ChannelBase> webhookChannels, BiFunction<Long, ChannelBase, Disposable> onError) {
        HttpHost target = new HttpHost("https", "discord.com");
        Map<String, SimpleHttpRequest> cacheReq = new HashMap<>();
        this.i18n.getLanguages().keySet().forEach(lang -> cacheReq.put(lang, SimpleRequestBuilder.post()
                .setHttpHost(target)
                .addHeader("User-Agent", "EEWBot")
                .setBody(webhookBodyByLang.get(lang), ContentType.APPLICATION_JSON)
                .build()));

        try {
            final Future<AsyncClientEndpoint> leaseFuture = this.asyncHttpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(30, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(webhookChannels.size());
                Map<Long, ChannelBase> erroredChannels = new ConcurrentHashMap<>();
                webhookChannels.forEach((channelId, channel) -> {
                    SimpleHttpRequest request = SimpleRequestBuilder.copy(cacheReq.get(channel.getLang()))
                            .setPath("/api/webhooks" + Objects.requireNonNull(channel.getWebhook()).getPath())
                            .build();
                    endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {
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
                    });
                });
                latch.await();
                if (!erroredChannels.isEmpty()) {
                    this.executor.execute(() -> {
                        Thread.currentThread().setName("eewbot-channel-unregister-thread");

                        erroredChannels.forEach((channelId, channel) -> {
                            Log.logger.info("Webhook {} is deleted, unregister", Objects.requireNonNull(channel.getWebhook()).getId());
                            this.channels.setWebhook(channelId, null);
                        });
                        try {
                            this.channels.save();
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

    private void sendDuplicator(boolean highPriority, URI duplicator, Map<String, String> webhookByLang, Map<Long, ChannelBase> webhookChannels, Consumer<Map<Long, ChannelBase>> onError) {
        try {
            long startTime = System.currentTimeMillis();

            HttpHost target = HttpHost.create(duplicator);
            Map<String, List<SimpleHttpRequest>> requestsByLang = new HashMap<>();
            this.i18n.getLanguages().keySet().forEach(lang -> requestsByLang.put(lang, new ArrayList<>()));
            Map<String, List<String>> webhooksByLang = new HashMap<>();
            this.i18n.getLanguages().keySet().forEach(lang -> webhooksByLang.put(lang, new ArrayList<>()));
            webhookChannels.forEach((channelId, channel) -> webhooksByLang.get(channel.getLang()).add(Objects.requireNonNull(channel.getWebhook()).getUrl()));

            AtomicInteger requestCount = new AtomicInteger();
            webhooksByLang.forEach((lang, webhooks) -> {
                if (webhooks.isEmpty())
                    return;

                int chunkSize = 1500;
                IntStream.iterate(0, n -> n + chunkSize)
                        .limit((webhooks.size() + chunkSize - 1) / chunkSize)
                        .mapToObj(start -> webhooks.subList(start, Math.min(start + chunkSize, webhooks.size())))
                        .forEach(chunk -> {
                            SimpleHttpRequest request = SimpleRequestBuilder.post()
                                    .setHttpHost(target)
                                    .addHeader("User-Agent", "EEWBot")
                                    .addHeader("X-Duplicate-Targets", EEWBot.GSON.toJson(chunk))
                                    .addHeader("X-Duplicate-Priority", highPriority ? "high" : "low")
                                    .setPath("/api/duplicate")
                                    .setBody(webhookByLang.get(lang), ContentType.APPLICATION_JSON)
                                    .build();
                            requestsByLang.get(lang).add(request);
                            requestCount.getAndIncrement();
                        });
            });

            final Future<AsyncClientEndpoint> leaseFuture = this.asyncHttpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(10, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(requestCount.get());
                requestsByLang.forEach((lang, requests) ->
                        requests.forEach(request -> endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {

                            @Override
                            public void completed(SimpleHttpResponse simpleHttpResponse) {
                                latch.countDown();
                                Log.logger.info("Sent message to duplicator: {}", simpleHttpResponse.getCode());
                                if (simpleHttpResponse.getCode() < 200 || simpleHttpResponse.getCode() >= 300) {
                                    Log.logger.info("Failed to send message to duplicator: {}", simpleHttpResponse.getCode());
                                    onError.accept(webhookChannels.entrySet().stream()
                                            .filter(entry -> entry.getValue().getLang().equals(lang))
                                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                                }
                            }

                            @Override
                            public void failed(Exception e) {
                                latch.countDown();
                                Log.logger.info("Failed to connect to duplicator: {}", e.getMessage());
                                onError.accept(webhookChannels.entrySet().stream()
                                        .filter(entry -> entry.getValue().getLang().equals(lang))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                            }

                            @Override
                            public void cancelled() {
                                latch.countDown();
                                Log.logger.info("Cancelled to connect to duplicator");
                                onError.accept(webhookChannels.entrySet().stream()
                                        .filter(entry -> entry.getValue().getLang().equals(lang))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                            }
                        })));
                latch.await();
            } finally {
                endpoint.releaseAndReuse();
            }
            Log.logger.info("Sent {} requests to duplicator in {}ms", requestCount.get(), System.currentTimeMillis() - startTime);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.logger.error("Failed to send message: Failed to connect to duplicator");
        }
    }

    public Mono<Message> directSendMessagePassErrors(long channelId, MessageCreateSpec spec) {
        return Mono.defer(() -> this.gateway.getRestClient().getChannelService()
                        .createMessage(channelId, spec.asRequest()))
                .map(data -> new Message(this.gateway, data));
    }

    public void handleDuplicatorNegativeCache() {
        Thread.currentThread().setName("eewbot-duplicator-metrics-thread");

        try {
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(new URIBuilder(this.duplicatorAddress).setPath("/api/negative_cache").build())
                    .header("User-Agent", "eewbot")
                    .build();
            HttpResponse<String> getResponse = this.httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            if (getResponse.statusCode() != 200) {
                Log.logger.error("Failed to fetch negative cache from duplicator: " + getResponse.statusCode() + " " + getResponse.body());
                return;
            }

            List<String> notFoundList = EEWBot.GSON.fromJson(getResponse.body(), new TypeToken<List<String>>() {
            }.getType());
            if (notFoundList.isEmpty()) {
                return;
            }

            notFoundList.stream().map(webhook -> Long.parseLong(webhook.substring(33, webhook.lastIndexOf("/"))))
                    .forEach(webhookId -> this.channels.actionOnChannels(ChannelFilter.builder().webhookId(webhookId).build(), channelId -> {
                        Log.logger.info("Webhook for channel {} is deleted, unregister", channelId);
                        Webhook current = this.channels.get(channelId).getWebhook();
                        if (current != null && current.getId() == webhookId)
                            this.channels.setWebhook(channelId, null);
                    }));

            HttpRequest delRequest = HttpRequest.newBuilder()
                    .DELETE()
                    .uri(new URIBuilder(this.duplicatorAddress).setPath("/api/negative_cache").build())
                    .header("User-Agent", "eewbot")
                    .header("X-Delete-Targets", EEWBot.GSON.toJson(notFoundList))
                    .build();
            HttpResponse<Void> delResponse = this.httpClient.send(delRequest, HttpResponse.BodyHandlers.discarding());
            if (delResponse.statusCode() != 200) {
                Log.logger.error("Failed to delete negative cache from duplicator: " + delResponse.statusCode());
            }
        } catch (IOException e) {
            Log.logger.error("Failed to fetch metrics from duplicator", e);
        } catch (InterruptedException e) {
            Log.logger.error("Interrupted while fetching errors from duplicator", e);
        } catch (URISyntaxException e) {
            Log.logger.error("Invalid duplicator metrics URI", e);
        }
    }
}
