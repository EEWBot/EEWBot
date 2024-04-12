package net.teamfruit.eewbot;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.discordjson.json.WebhookCreateRequest;
import discord4j.rest.util.Permission;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEW;
import net.teamfruit.eewbot.entity.other.KmoniEEW;
import net.teamfruit.eewbot.entity.other.NHKDetailQuakeInfo;
import net.teamfruit.eewbot.gateway.*;
import net.teamfruit.eewbot.registry.ChannelFilter;
import net.teamfruit.eewbot.registry.ChannelRegistry;
import net.teamfruit.eewbot.registry.Config;
import net.teamfruit.eewbot.registry.Webhook;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EEWExecutor {

    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService messageExecutor;
    private final TimeProvider timeProvider;
    private final EEWService service;
    private final Config config;
    private final long applicationId;
    private final GatewayDiscordClient client;
    private final ChannelRegistry channels;

    public EEWExecutor(final EEWService service, final Config config, long applicationId, ScheduledExecutorService executor, GatewayDiscordClient client, ChannelRegistry channels) {
        this.service = service;
        this.config = config;
        this.applicationId = applicationId;
        this.scheduledExecutor = executor;

        this.messageExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "eewbot-send-message-thread"));
        this.timeProvider = new TimeProvider(this.scheduledExecutor);
        this.client = client;
        this.channels = channels;
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
                    boolean isImportant = prev == null ||
                            eew.isInitial() ||
                            eew.isFinal() ||
                            eew.isAlert() != prev.isAlert() ||
                            !eew.getIntensity().equals(prev.getIntensity()) ||
                            !eew.getRegionName().equals(prev.getRegionName());
                    SeismicIntensity maxIntensity = eew.getMaxIntensityEEW();

                    ChannelFilter.Builder builder = ChannelFilter.builder();
                    if (isWarning)
                        builder.eewAlert(true);
                    else
                        builder.eewPrediction(true);
                    if (!isImportant)
                        builder.eewDecimation(false);
                    builder.intensity(maxIntensity);
                    EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.service.sendMessage(builder.build(), eew, true));
                }
            }, 0, this.config.getKyoshinDelay(), TimeUnit.SECONDS);
        } else {
            DmdataGateway dmdataGateway = new DmdataGateway(new DmdataAPI(this.config.getDmdataAPIKey(), this.config.getDmdataOrigin()), this.applicationId, this.config.isDmdataMultiSocketConnect(), this.config.isDebug()) {
                @Override
                public void onNewData(DmdataEEW eew) {
                    if (eew.getBody().getEarthquake() != null &&
                            StringUtils.equals(eew.getBody().getEarthquake().getCondition(), "仮定震源要素") &&
                            eew.getBody().getIntensity() == null)
                        return;

                    DmdataEEW.Body currentBody = eew.getBody();
                    DmdataEEW.Body prevBody = eew.getPrev() != null ? eew.getPrev().getBody() : null;

                    boolean isWarning = currentBody.isCanceled() ? prevBody != null && prevBody.isWarning() : currentBody.isWarning();

                    DmdataEEW.Body.Intensity currentIntensity = currentBody.getIntensity();
                    DmdataEEW.Body.Intensity prevIntensity = prevBody != null ? prevBody.getIntensity() : null;
                    boolean isImportant = prevBody == null ||
                            currentBody.isLastInfo() ||
                            currentBody.isWarning() != prevBody.isWarning() ||
                            (currentIntensity == null) != (prevIntensity == null) ||
                            currentIntensity != null && !currentIntensity.getForecastMaxInt().getFrom().equals(prevIntensity.getForecastMaxInt().getFrom()) ||
                            !currentBody.getEarthquake().getHypocenter().getName().equals(prevBody.getEarthquake().getHypocenter().getName());

                    SeismicIntensity maxIntensity = eew.getMaxIntensityEEW();

                    ChannelFilter.Builder builder = ChannelFilter.builder();
                    if (isWarning)
                        builder.eewAlert(true);
                    else
                        builder.eewPrediction(true);
                    if (!isImportant)
                        builder.eewDecimation(false);
                    builder.intensity(maxIntensity);
                    EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.service.sendMessage(builder.build(), eew, true));
                }
            };
            this.scheduledExecutor.execute(dmdataGateway);
            this.scheduledExecutor.scheduleAtFixedRate(new DmdataWsLivenessChecker(dmdataGateway), 30, 30, TimeUnit.SECONDS);
        }

        this.scheduledExecutor.scheduleAtFixedRate(new QuakeInfoGateway() {

            @Override
            public void onNewData(final NHKDetailQuakeInfo data) {
                Log.logger.info(data.toString());

                ChannelFilter.Builder builder = ChannelFilter.builder();
                builder.quakeInfo(true);
                builder.intensity(data.getEarthquake().getIntensity());
                EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.service.sendMessage(builder.build(), data, false));
            }
        }, 0, this.config.getQuakeInfoDelay(), TimeUnit.SECONDS);

        this.scheduledExecutor.scheduleAtFixedRate(new JMAEqVolGateway() {
            @Override
            public void onNewData(JMAEqVolGateway data) {
                Log.logger.info(data.toString());
            }
        }, 0, this.config.getQuakeInfoDelay(), TimeUnit.SECONDS);

        if (StringUtils.isNotEmpty(this.config.getDuplicatorAddress())) {
            this.scheduledExecutor.scheduleAtFixedRate(EEWExecutor.this.service::handleDuplicatorMetrics, 15, 15, TimeUnit.SECONDS);
        }

        if (this.config.isWebhookMigration())
            this.scheduledExecutor.scheduleWithFixedDelay(() -> {
                Thread.currentThread().setName("eewbot-webhook-migration-thread");

                this.channels.getWebhookAbsentChannels()
                        .forEach(channelId -> {
                            this.client.getChannelById(Snowflake.of(channelId))
                                    .filter(GuildChannel.class::isInstance)
                                    .cast(GuildChannel.class)
                                    .filterWhen(guildChannel -> guildChannel.getEffectivePermissions(this.client.getSelfId())
                                            .map(permissions -> permissions.contains(Permission.MANAGE_WEBHOOKS)))
                                    .flatMap(guildChannel -> guildChannel.getGuild()
                                            .flatMap(guild -> guild.getSelfMember().map(PartialMember::getDisplayName))
                                            .flatMap(name -> {
                                                if (guildChannel instanceof ThreadChannel) {
                                                    ThreadChannel threadChannel = (ThreadChannel) guildChannel;
                                                    Optional<Snowflake> parentId = threadChannel.getParentId();
                                                    if (parentId.isEmpty())
                                                        return Mono.empty();
                                                    return this.client.getRestClient().getWebhookService()
                                                            .createWebhook(parentId.map(Snowflake::asLong).get(), WebhookCreateRequest.builder()
                                                                    .name(name)
                                                                    .build(), "Create EEWBot webhook");
                                                }
                                                return this.client.getRestClient().getWebhookService()
                                                        .createWebhook(guildChannel.getId().asLong(), WebhookCreateRequest.builder()
                                                                .name(name)
                                                                .build(), "Create EEWBot webhook");
                                            }).flatMap(webhookData -> Mono.fromRunnable(() -> {
                                                boolean isThread = guildChannel instanceof ThreadChannel;
                                                Webhook webhook = new Webhook(webhookData.id().asLong(), webhookData.token().get(), isThread ? channelId : null);
                                                this.channels.setWebhook(channelId, webhook);
                                                try {
                                                    this.channels.save();
                                                } catch (IOException e) {
                                                    Log.logger.error("Failed to save channels during webhook creation batch", e);
                                                }
                                                Log.logger.info("Created webhook for " + channelId);
                                            })))
                                    .subscribe();
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                Log.logger.error("Failed to sleep during webhook creation batch", e);
                            }
                        });
            }, 1, 60 * 12, TimeUnit.MINUTES);
    }

}
