package net.teamfruit.eewbot;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.WebhookCreateRequest;
import discord4j.rest.http.client.ClientException;
import net.teamfruit.eewbot.entity.DetailQuakeInfo;
import net.teamfruit.eewbot.entity.DmdataEEW;
import net.teamfruit.eewbot.entity.KmoniEEW;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.gateway.*;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Config;
import net.teamfruit.eewbot.registry.ConfigurationRegistry;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class EEWExecutor {

    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService messageExcecutor;
    private final TimeProvider timeProvider;
    private final EEWService service;
    private final Config config;
    private final long applicationId;
    private final GatewayDiscordClient client;
    private final Map<Long, Channel> channels;
    private final ConfigurationRegistry<Map<Long, Channel>> channelRegistry;

    public EEWExecutor(final EEWService service, final Config config, long applicationId, ScheduledExecutorService executor, GatewayDiscordClient client, Map<Long, Channel> channels, ConfigurationRegistry<Map<Long, Channel>> channelRegistry) {
        this.service = service;
        this.config = config;
        this.applicationId = applicationId;
        this.scheduledExecutor = executor;

        this.messageExcecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "eewbot-send-message-thread"));
        this.timeProvider = new TimeProvider(this.scheduledExecutor);
        this.client = client;
        this.channels = channels;
        this.channelRegistry = channelRegistry;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return this.scheduledExecutor;
    }

    public TimeProvider getTimeProvider() {
        return this.timeProvider;
    }

    public void init() {
        if (this.config.isEnableKyoshin()) {
            this.timeProvider.init();

            this.scheduledExecutor.scheduleAtFixedRate(new KmoniGateway(this.timeProvider) {

                @Override
                public void onNewData(final KmoniEEW eew) {
                    Log.logger.info(eew.toString());
                    KmoniEEW prev = eew.getPrev();

                    boolean isWarning = eew.isCancel() ? prev != null && prev.isAlert() : eew.isAlert();
                    Predicate<Channel> warning = c -> isWarning ? c.eewAlert : c.eewPrediction;

                    boolean isImportant = prev == null ||
                            eew.isInitial() ||
                            eew.isFinal() ||
                            eew.isAlert() != prev.isAlert() ||
                            !eew.getIntensity().equals(prev.getIntensity()) ||
                            !eew.getRegionName().equals(prev.getRegionName());
                    Predicate<Channel> decimation = c -> !c.eewDecimation || isImportant;

                    SeismicIntensity maxIntensity = eew.getMaxIntensityEEW();
                    Predicate<Channel> sensitivity = c -> c.minIntensity.compareTo(maxIntensity) <= 0;
                    EEWExecutor.this.messageExcecutor.submit(() -> EEWExecutor.this.service.sendMessage(warning.and(decimation).and(sensitivity), eew, true));
                }
            }, 0, this.config.getKyoshinDelay(), TimeUnit.SECONDS);
        } else {
            DmdataGateway dmdataGateway = new DmdataGateway(new DmdataAPI(this.config.getDmdataAPIKey(), this.config.getDmdataOrigin()), applicationId, this.config.isDmdataMultiSocketConnect(), this.config.isDebug()) {
                @Override
                public void onNewData(DmdataEEW eew) {
                    DmdataEEW.Body currentBody = eew.getBody();
                    DmdataEEW.Body prevBody = eew.getPrev() != null ? eew.getPrev().getBody() : null;

                    boolean isWarning = currentBody.isCanceled() ? prevBody != null && prevBody.isWarning() : currentBody.isWarning();
                    Predicate<Channel> warning = c -> isWarning ? c.eewAlert : c.eewPrediction;

                    DmdataEEW.Body.Intensity currentIntensity = currentBody.getIntensity();
                    DmdataEEW.Body.Intensity prevIntensity = prevBody != null ? prevBody.getIntensity() : null;
                    boolean isImportant = prevBody == null ||
                            currentBody.isLastInfo() ||
                            currentBody.isWarning() != prevBody.isWarning() ||
                            (currentIntensity == null) != (prevIntensity == null) ||
                            currentIntensity != null && !currentIntensity.getForecastMaxInt().getFrom().equals(prevIntensity.getForecastMaxInt().getFrom()) ||
                            !currentBody.getEarthquake().getHypocenter().getName().equals(prevBody.getEarthquake().getHypocenter().getName());
                    Predicate<Channel> decimation = c -> !c.eewDecimation || isImportant;

                    SeismicIntensity maxIntensity = eew.getMaxIntensityEEW();
                    Predicate<Channel> sensitivity = c -> c.minIntensity.compareTo(maxIntensity) <= 0;
                    EEWExecutor.this.messageExcecutor.submit(() -> EEWExecutor.this.service.sendMessage(warning.and(decimation).and(sensitivity), eew, true));
                }
            };
            this.scheduledExecutor.execute(dmdataGateway);
            this.scheduledExecutor.scheduleAtFixedRate(new DmdataWsLivenessChecker(dmdataGateway), 30, 30, TimeUnit.SECONDS);
        }

        this.scheduledExecutor.scheduleAtFixedRate(new QuakeInfoGateway() {

            @Override
            public void onNewData(final DetailQuakeInfo data) {
                Log.logger.info(data.toString());

                final Predicate<Channel> quakeInfo = c -> c.quakeInfo;
                final Predicate<Channel> sensitivity = c -> c.minIntensity.compareTo(data.getEarthquake().getIntensity()) <= 0;
                EEWExecutor.this.messageExcecutor.submit(() -> EEWExecutor.this.service.sendMessage(quakeInfo.and(sensitivity), data, false));
            }
        }, 0, this.config.getQuakeInfoDelay(), TimeUnit.SECONDS);

        if (StringUtils.isNotEmpty(this.config.getDuplicatorAddress())) {
            this.scheduledExecutor.scheduleAtFixedRate(EEWExecutor.this.service::handleDuplicatorMetrics, 60, 60, TimeUnit.SECONDS);
        }

        this.scheduledExecutor.execute(() -> {
            this.channels.entrySet().stream()
                    .filter(entry -> entry.getValue().webhook == null)
                    .forEach(entry -> {
                        this.client.getChannelById(Snowflake.of(entry.getKey()))
                                .onErrorComplete(ClientException.class)
                                .flatMap(channel -> channel.getRestChannel().getData().map(ChannelData::guildId))
                                .flatMap(guildId -> {
                                    if (!guildId.isAbsent()) {
                                        return this.client.getGuildById(Snowflake.of(guildId.get()))
                                                .flatMap(guild -> guild.getSelfMember().map(PartialMember::getDisplayName)
                                                        .flatMap(name -> this.client.getRestClient().getWebhookService()
                                                                .createWebhook(entry.getKey(), WebhookCreateRequest.builder()
                                                                        .name(name)
                                                                        .build(), "Create EEWBot webhook")));
                                    }
                                    return Mono.empty();
                                }).subscribe(webhookData -> {
                                    this.client.getChannelById(Snowflake.of(entry.getKey()))
                                            .subscribe(channel -> {
                                                boolean isThread = channel instanceof ThreadChannel;
                                                Channel botChannel = entry.getValue();
                                                Channel.Webhook webhook = new Channel.Webhook(webhookData.id().asString(), webhookData.token().get(), isThread ? String.valueOf(entry.getKey()) : null);
                                                if (!webhook.equals(botChannel.webhook)) {
                                                    botChannel.webhook = webhook;
                                                    try {
                                                        this.channelRegistry.save();
                                                    } catch (IOException e) {
                                                        Log.logger.error("Failed to save channels during webhook creation batch", e);
                                                    }
                                                    Log.logger.info("Created webhook for " + entry.getKey());
                                                }
                                            });
                                });
                    });
        });
    }

}
