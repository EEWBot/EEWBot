package net.teamfruit.eewbot;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.entity.DiscordWebhook;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EEWService {

    private final GatewayDiscordClient gateway;
    private final Map<Long, Channel> channels;
    private final String avatarUrl;
    private final ReentrantReadWriteLock lock;
    private final Optional<TextChannel> systemChannel;
    private final MinimalHttpAsyncClient httpClient;

    public EEWService(EEWBot bot) {
        this.gateway = bot.getClient();
        this.channels = bot.getChannels();
        this.avatarUrl = bot.getAvatarUrl();
        this.lock = bot.getChannelsLock();
        this.systemChannel = bot.getSystemChannel();
        int poolingMax = bot.getConfig().getPoolingMax();
        int poolingMaxPerRoute = bot.getConfig().getPoolingMaxPerRoute();
        PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(poolingMax)
                .setMaxConnPerRoute(poolingMaxPerRoute)
                .build();
        this.httpClient = HttpAsyncClients.createMinimal(
                H2Config.DEFAULT,
                Http1Config.DEFAULT,
                IOReactorConfig.DEFAULT,
                connectionManager);
        this.httpClient.start();
    }

    public void sendMessage(final Predicate<Channel> filter, final Entity entity, boolean highPriority) {
        Map<String, MessageCreateSpec> cacheMsg = new HashMap<>();
        I18n.INSTANCE.getLanguages().keySet().forEach(lang -> cacheMsg.put(lang, entity.createMessage(lang)));

        Map<Boolean, List<Map.Entry<Long, Channel>>> webhookPartitioned = this.channels.entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().webhook != null));

        Map<String, String> cacheWebhook = new HashMap<>();
        I18n.INSTANCE.getLanguages().keySet().forEach(lang -> {
            DiscordWebhook webhook = entity.createWebhook(lang);
            webhook.avatar_url = this.avatarUrl;
            cacheWebhook.put(lang, webhook.json());
        });

        this.lock.readLock().lock();

        String duplicatorAddress = EEWBot.instance.getConfig().getDuplicatorAddress();
        if (StringUtils.isEmpty(duplicatorAddress)) {
            sendWebhook(cacheWebhook, webhookPartitioned.get(true), (id, channel) -> directSendMessage(id, cacheMsg.get(channel.lang)).subscribe());
        } else {
            try {
                sendDuplicator(highPriority, new URI(duplicatorAddress), cacheWebhook, webhookPartitioned.get(true), channels ->
                        sendWebhook(cacheWebhook, channels, (id, channel) -> directSendMessage(id, cacheMsg.get(channel.lang)).subscribe()));
            } catch (URISyntaxException e) {
                Log.logger.error("Webhook send fallback: Invalid duplicator address");
                sendWebhook(cacheWebhook, webhookPartitioned.get(true), (id, channel) -> directSendMessage(id, cacheMsg.get(channel.lang)).subscribe());
            }
        }

//        partitioned.get(false).forEach(entry -> {
//            MessageCreateSpec spec = cacheMsg.get(entry.getValue().lang);
//            directSendMessage(entry.getKey(), spec).subscribe();
//        });

        Flux.merge(webhookPartitioned.get(false).stream()
                        .map(entry -> directSendMessage(entry.getKey(), cacheMsg.get(entry.getValue().lang)))
                        .collect(Collectors.toList()))
                .parallel()
                .runOn(Schedulers.parallel())
                .groups()
                .subscribe(Flux::subscribe);

        this.lock.readLock().unlock();
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
            final Future<AsyncClientEndpoint> leaseFuture = this.httpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(30, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(webhookChannels.size());
                webhookChannels.forEach(entry -> {
                    SimpleHttpRequest request = cacheReq.get(entry.getValue().lang);
                    request.setPath("/api/webhooks" + entry.getValue().webhook.getJoined());
                    endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {
                        @Override
                        public void completed(SimpleHttpResponse simpleHttpResponse) {
                            latch.countDown();
                            if (simpleHttpResponse.getCode() < 200 || simpleHttpResponse.getCode() >= 300) {
                                onError.apply(entry.getKey(), entry.getValue());
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
                            .filter(entry -> entry.getValue().lang.equals(lang))
                            .map(entry -> "https://discord.com/api/webhooks" + entry.getValue().webhook.getJoined())
                            .toArray(String[]::new)))
                    .setPath(highPriority ? "/duplicate/high_priority" : "/duplicate/low_priority")
                    .setBody(webhookByLang.get(lang), ContentType.APPLICATION_JSON)
                    .build()));
            final Future<AsyncClientEndpoint> leaseFuture = this.httpClient.lease(target, null);
            final AsyncClientEndpoint endpoint = leaseFuture.get(10, TimeUnit.SECONDS);
            try {
                final CountDownLatch latch = new CountDownLatch(I18n.INSTANCE.getLanguages().size());
                requestsByLang.forEach((lang, request) -> endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {
                    
                    @Override
                    public void completed(SimpleHttpResponse simpleHttpResponse) {
                        latch.countDown();
                        if (simpleHttpResponse.getCode() < 200 || simpleHttpResponse.getCode() >= 300) {
                            onError.accept(webhookChannels.stream().filter(entry -> entry.getValue().lang.equals(lang)).collect(Collectors.toList()));
                        }
                    }

                    @Override
                    public void failed(Exception e) {
                        latch.countDown();
                        Log.logger.info("Failed to connect to duplicator: {}", e.getMessage());
                        onError.accept(webhookChannels.stream().filter(entry -> entry.getValue().lang.equals(lang)).collect(Collectors.toList()));
                    }

                    @Override
                    public void cancelled() {
                        latch.countDown();
                        Log.logger.info("Cancelled to connect to duplicator");
                        onError.accept(webhookChannels.stream().filter(entry -> entry.getValue().lang.equals(lang)).collect(Collectors.toList()));
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

    public Mono<Message> sendMessagePassErrors(long channelId, final MessageCreateSpec spec) {
        return directSendMessagePassErrors(channelId, spec);
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

    private Mono<Message> directSendMessage(final long channelId, final MessageCreateSpec spec) {
        return directSendMessagePassErrors(channelId, spec)
                .doOnError(ClientException.class, err -> {
                    Log.logger.error("Failed to send message: ChannelID={} Message={}", channelId, err.getMessage());
                    if (err.getStatus() == HttpResponseStatus.NOT_FOUND || err.getStatus() == HttpResponseStatus.FORBIDDEN) {
                        this.lock.writeLock().lock();
                        if (this.channels.remove(channelId) != null)
                            Log.logger.info(err.getStatus() == HttpResponseStatus.NOT_FOUND ? "Channel {} has been deleted, unregister" : "Missing permissions {}", channelId);
                        this.lock.writeLock().unlock();
                    }
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Message> directSendMessagePassErrors(long channelId, MessageCreateSpec spec) {
        return Mono.defer(() -> this.gateway.getRestClient().getChannelService()
                        .createMessage(channelId, spec.asRequest()))
                .map(data -> new Message(this.gateway, data));
    }
}
