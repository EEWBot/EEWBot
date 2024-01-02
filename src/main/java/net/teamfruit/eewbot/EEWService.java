package net.teamfruit.eewbot;

import com.google.gson.reflect.TypeToken;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.DiscordWebhook;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.ChannelRegistry;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EEWService {

    private final GatewayDiscordClient gateway;
    private final String avatarUrl;
    private final ScheduledExecutorService executor;
    private final ChannelRegistry channels;
    //    private final Optional<TextChannel> systemChannel;
    private final HttpClient httpClient;
    private final MinimalHttpAsyncClient asyncHttpClient;
    private final URI duplicatorAddress;

    public EEWService(EEWBot bot) {
        this.gateway = bot.getClient();
        this.channels = bot.getChannels();
        this.avatarUrl = bot.getAvatarUrl();
        this.executor = bot.getScheduledExecutor();
//        this.systemChannel = bot.getSystemChannel();
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

        if (StringUtils.isNotEmpty(bot.getConfig().getDuplicatorAddress())) {
            try {
                this.duplicatorAddress = new URI(bot.getConfig().getDuplicatorAddress());
            } catch (URISyntaxException e) {
                // should not happen
                throw new RuntimeException(e);
            }
        } else
            this.duplicatorAddress = null;
    }

    public void sendMessage(final Predicate<Channel> filter, final Entity entity, boolean highPriority) {
        Map<String, MessageCreateSpec> msgByLang = new HashMap<>();
        I18n.INSTANCE.getLanguages().keySet().forEach(lang -> msgByLang.put(lang, entity.createMessage(lang)));

        Map<Boolean, List<Map.Entry<Long, Channel>>> webhookPartitioned = this.channels.getChannelsPartitionedByWebhookPresent(filter);

        Map<String, String> cacheWebhook = new HashMap<>();
        I18n.INSTANCE.getLanguages().keySet().forEach(lang -> {
            DiscordWebhook webhook = entity.createWebhook(lang);
            webhook.avatar_url = this.avatarUrl;
            cacheWebhook.put(lang, webhook.json());
        });

        if (this.duplicatorAddress == null) {
            sendWebhook(cacheWebhook, webhookPartitioned.get(true), (id, channel) -> directSendMessagePassErrors(id, msgByLang.get(channel.getLang())).subscribe());
        } else {
            sendDuplicator(highPriority, this.duplicatorAddress, cacheWebhook, webhookPartitioned.get(true), channels ->
                    sendWebhook(cacheWebhook, channels, (id, channel) -> directSendMessagePassErrors(id, msgByLang.get(channel.getLang())).subscribe()));
        }

//        partitioned.get(false).forEach(entry -> {
//            MessageCreateSpec spec = cacheMsg.get(entry.getValue().lang);
//            directSendMessage(entry.getKey(), spec).subscribe();
//        });

        sendMessageD4J(webhookPartitioned.get(false), msgByLang);
    }

    private void sendMessageD4J(List<Map.Entry<Long, Channel>> channels, Map<String, MessageCreateSpec> msgByLang) {
        List<Long> erroredChannels = new ArrayList<>();
        Flux.merge(channels.stream()
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

    private void sendWebhook(Map<String, String> webhookBodyByLang, List<Map.Entry<Long, Channel>> webhookChannels, BiFunction<Long, Channel, Disposable> onError) {
        HttpHost target = new HttpHost("https", "discord.com");
        Map<String, SimpleHttpRequest> cacheReq = new HashMap<>();
        I18n.INSTANCE.getLanguages().keySet().forEach(lang -> cacheReq.put(lang, SimpleRequestBuilder.post()
                .setHttpHost(target)
                .addHeader("User-Agent", "EEWBot")
                .setBody(webhookBodyByLang.get(lang), ContentType.APPLICATION_JSON)
                .build()));

        try {
            final Future<AsyncClientEndpoint> leaseFuture = this.asyncHttpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(30, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(webhookChannels.size());
                Map<Long, Channel> erroredChannels = new ConcurrentHashMap<>();
                webhookChannels.forEach(entry -> {
                    SimpleHttpRequest request = cacheReq.get(entry.getValue().getLang());
                    request.setPath("/api/webhooks" + Objects.requireNonNull(entry.getValue().getWebhook()).getPath());
                    endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {
                        @Override
                        public void completed(SimpleHttpResponse simpleHttpResponse) {
                            latch.countDown();
                            if (simpleHttpResponse.getCode() < 200 || simpleHttpResponse.getCode() >= 300) {
                                onError.apply(entry.getKey(), entry.getValue());
                            }
                            if (simpleHttpResponse.getCode() == 404) {
                                erroredChannels.put(entry.getKey(), entry.getValue());
                            }
                        }

                        @Override
                        public void failed(Exception e) {
                            latch.countDown();
                            Log.logger.info("Failed to send webhook: ChannelID={} Message={}", entry.getKey(), e.getMessage());
                            onError.apply(entry.getKey(), entry.getValue());
                        }

                        @Override
                        public void cancelled() {
                            latch.countDown();
                            Log.logger.info("Cancelled to send webhook: ChannelID={}", entry.getKey());
                            onError.apply(entry.getKey(), entry.getValue());
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

    private void sendDuplicator(boolean highPriority, URI duplicator, Map<String, String> webhookByLang, List<Map.Entry<Long, Channel>> webhookChannels, Consumer<List<Map.Entry<Long, Channel>>> onError) {
        try {
            HttpHost target = HttpHost.create(duplicator);
            Map<String, SimpleHttpRequest> requestsByLang = new HashMap<>();
            I18n.INSTANCE.getLanguages().keySet().forEach(lang -> requestsByLang.put(lang, SimpleRequestBuilder.post()
                    .setHttpHost(target)
                    .addHeader("User-Agent", "EEWBot")
                    .addHeader("X-Duplicate-Targets", EEWBot.GSON.toJson(webhookChannels.stream()
                            .filter(entry -> entry.getValue().getLang().equals(lang))
                            .map(entry -> Objects.requireNonNull(entry.getValue().getWebhook()).getUrl())
                            .toArray(String[]::new)))
                    .setPath(highPriority ? "/duplicate/high_priority" : "/duplicate/low_priority")
                    .setBody(webhookByLang.get(lang), ContentType.APPLICATION_JSON)
                    .build()));
            final Future<AsyncClientEndpoint> leaseFuture = this.asyncHttpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(10, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(I18n.INSTANCE.getLanguages().size());
                requestsByLang.forEach((lang, request) -> endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {

                    @Override
                    public void completed(SimpleHttpResponse simpleHttpResponse) {
                        latch.countDown();
                        if (simpleHttpResponse.getCode() < 200 || simpleHttpResponse.getCode() >= 300) {
                            onError.accept(webhookChannels.stream().filter(entry -> entry.getValue().getLang().equals(lang)).collect(Collectors.toList()));
                        }
                    }

                    @Override
                    public void failed(Exception e) {
                        latch.countDown();
                        Log.logger.info("Failed to connect to duplicator: {}", e.getMessage());
                        onError.accept(webhookChannels.stream().filter(entry -> entry.getValue().getLang().equals(lang)).collect(Collectors.toList()));
                    }

                    @Override
                    public void cancelled() {
                        latch.countDown();
                        Log.logger.info("Cancelled to connect to duplicator");
                        onError.accept(webhookChannels.stream().filter(entry -> entry.getValue().getLang().equals(lang)).collect(Collectors.toList()));
                    }
                }));
                latch.await();
            } finally {
                endpoint.releaseAndReuse();
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.logger.error("Failed to send message: Failed to connect to duplicator");
        }
    }

//    public void sendAttachment(final String key, final Function<String, MessageCreateSpec> spec) {
//        sendAttachment(channel -> channel.value(key), spec);
//    }

//    public void sendAttachment(final Predicate<Channel> filter, final Function<String, MessageCreateSpec> spec) {
//        if (this.systemChannel.isPresent())
//            directSendMessage(this.systemChannel.get().getId().asLong(), spec.apply(null))
//                    .map(msg -> msg.getAttachments().iterator().next().getUrl())
//                    .subscribe(url -> sendMessage(filter, lang -> MessageCreateSpec.builder().content(url).build()));
//        else
//            sendMessage(filter, spec);
//    }

    public Mono<Message> directSendMessagePassErrors(long channelId, MessageCreateSpec spec) {
        return Mono.defer(() -> this.gateway.getRestClient().getChannelService()
                        .createMessage(channelId, spec.asRequest()))
                .map(data -> new Message(this.gateway, data));
    }

    public void handleDuplicatorMetrics() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(this.duplicatorAddress)
                    .header("User-Agent", "eewbot")
                    .build();
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Set<String>> resultMap = EEWBot.GSON.fromJson(response.body(), new TypeToken<Map<String, Set<String>>>() {
                }.getType());
                Set<String> notFoundList = resultMap.get("404");
                if (notFoundList != null) {
                    this.channels.getChannels(channel -> channel.getWebhook() != null && notFoundList.contains(channel.getWebhook().getUrl()))
                            .forEach((key, value) -> {
                                Log.logger.info("Webhook {} is deleted, unregister", Objects.requireNonNull(value.getWebhook()).getId());
                                this.channels.setWebhook(key, null);
                            });
                }
            } else {
                Log.logger.error("Failed to fetch errors from duplicator: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Log.logger.error("Interrupted while fetching errors from duplicator", e);
        }
    }
}
