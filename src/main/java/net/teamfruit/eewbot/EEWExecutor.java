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
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.VTSE41;
import net.teamfruit.eewbot.entity.other.KmoniEEW;
import net.teamfruit.eewbot.entity.other.NHKDetailQuakeInfo;
import net.teamfruit.eewbot.gateway.*;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Calendar;
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
    private final ConfigV2 config;
    private final long applicationId;
    private final GatewayDiscordClient client;
    private final DestinationAdminRegistry adminRegistry;
    private final QuakeInfoStore quakeInfoStore;
    private final ExternalWebhookService externalWebhookService;

    public EEWExecutor(final EEWService service, final ConfigV2 config, long applicationId, ScheduledExecutorService executor, GatewayDiscordClient client, DestinationAdminRegistry adminRegistry, QuakeInfoStore quakeInfoStore, ExternalWebhookService externalWebhookService) {
        this.service = service;
        this.config = config;
        this.applicationId = applicationId;
        this.scheduledExecutor = executor;

        this.messageExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "eewbot-send-message-thread"));
        this.timeProvider = new TimeProvider(this.scheduledExecutor);
        this.client = client;
        this.adminRegistry = adminRegistry;
        this.quakeInfoStore = quakeInfoStore;
        this.externalWebhookService = externalWebhookService;
    }

    public TimeProvider getTimeProvider() {
        return this.timeProvider;
    }

    public void init() {
        if (this.config.getLegacy().isEnableKyoshin()) {
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
                    EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.service.sendMessage(builder.build(), eew));
                }
            }, 0, this.config.getLegacy().getKyoshinDelay(), TimeUnit.SECONDS);
        } else {
            DmdataGateway dmdataGateway = new DmdataGateway(new DmdataAPI(this.config.getDmdata().getAPIKey(), this.config.getDmdata().getOrigin()), this.applicationId, this.config.getDmdata().isMultiSocketConnect()) {
                @Override
                public void onNewData(DmdataEEW eew) {
                    if (eew.getBody().getEarthquake() != null &&
                            Strings.CS.equals(eew.getBody().getEarthquake().getCondition(), "仮定震源要素") &&
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
                    EEWExecutor.this.messageExecutor.submit(() -> {
                        EEWExecutor.this.service.sendMessage(builder.build(), eew);
                        EEWExecutor.this.externalWebhookService.sendExternalWebhook(eew);
                    });
                }
            };
            this.scheduledExecutor.execute(dmdataGateway);
            this.scheduledExecutor.scheduleAtFixedRate(new DmdataWsLivenessChecker(dmdataGateway), 30, 30, TimeUnit.SECONDS);
        }

        if (this.config.getLegacy().isEnableLegacyQuakeInfo()) {
            this.scheduledExecutor.scheduleAtFixedRate(new QuakeInfoGateway() {

                @Override
                public void onNewData(final NHKDetailQuakeInfo data) {
                    Log.logger.info(data.toString());

                    ChannelFilter.Builder builder = ChannelFilter.builder();
                    builder.quakeInfo(true);
                    builder.intensity(data.getEarthquake().getIntensity());
                    EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.service.sendMessage(builder.build(), data));
                }
            }, 0, this.config.getLegacy().getLegacyQuakeInfoDelay(), TimeUnit.SECONDS);
        }

        int currentSecond = Calendar.getInstance().get(Calendar.SECOND);
        int jmaXMLInitialDelay = 20 - currentSecond;
        if (jmaXMLInitialDelay < 0) {
            jmaXMLInitialDelay += 60;
        }

        this.scheduledExecutor.scheduleAtFixedRate(new JMAXmlGateway(this.quakeInfoStore) {
            @Override
            public void onNewData(AbstractJMAReport data) {
                if (!EEWExecutor.this.config.getLegacy().isEnableLegacyQuakeInfo()) {
                    if (data instanceof QuakeInfo quakeInfo) {
                        ChannelFilter filter = ChannelFilter.builder()
                                .quakeInfo(true)
                                .intensity(quakeInfo.getQuakeInfoMaxInt().orElse(SeismicIntensity.UNKNOWN))
                                .build();
                        EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.service.sendMessage(filter, data));
                    }
                }
                if (data instanceof VTSE41) {
                    ChannelFilter filter = ChannelFilter.builder()
                            .tsunami(true)
                            .build();
                    EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.service.sendMessage(filter, data));
                }
                if (data instanceof ExternalData externalData) {
                    EEWExecutor.this.messageExecutor.submit(() -> EEWExecutor.this.externalWebhookService.sendExternalWebhook(externalData));
                }
            }
        }, jmaXMLInitialDelay, 60, TimeUnit.SECONDS);

        this.scheduledExecutor.execute(new JMAXmlLGateway(this.quakeInfoStore));

        if (StringUtils.isNotEmpty(this.config.getWebhookSender().getAddress())) {
            this.scheduledExecutor.scheduleAtFixedRate(EEWExecutor.this.service::handleWebhookSenderNotFounds, 15, 15, TimeUnit.SECONDS);
        }

        if (this.config.getAdvanced().isWebhookMigration())
            this.scheduledExecutor.scheduleWithFixedDelay(() -> {
                Thread.currentThread().setName("eewbot-webhook-migration-thread");

                this.adminRegistry.getWebhookAbsentChannels()
                        .forEach(channelId -> {
                            this.client.getChannelById(Snowflake.of(channelId))
                                    .filter(GuildChannel.class::isInstance)
                                    .cast(GuildChannel.class)
                                    .filterWhen(guildChannel -> {
                                        Mono<GuildChannel> permissionCheckChannel;
                                        if (guildChannel instanceof ThreadChannel) {
                                            permissionCheckChannel = guildChannel.getClient().getChannelById(((ThreadChannel) guildChannel).getParentId().orElseThrow())
                                                    .cast(GuildChannel.class)
                                                    .switchIfEmpty(Mono.just(guildChannel));
                                        } else {
                                            permissionCheckChannel = Mono.just(guildChannel);
                                        }
                                        return permissionCheckChannel.flatMap(target -> target.getEffectivePermissions(this.client.getSelfId())
                                                .map(permissions -> permissions.contains(Permission.MANAGE_WEBHOOKS)));
                                    })
                                    .flatMap(guildChannel -> guildChannel.getGuild()
                                            .flatMap(guild -> guild.getSelfMember().map(PartialMember::getDisplayName))
                                            .flatMap(name -> {
                                                if (guildChannel instanceof ThreadChannel threadChannel) {
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
                                            }).flatMap(webhookData -> guildChannel.getClient().getChannelById(Snowflake.of(channelId))
                                                    .map(channel -> channel instanceof ThreadChannel ? channelId : null)
                                                    .defaultIfEmpty((Long) null)
                                                    .flatMap(threadId -> Mono.fromRunnable(() -> {
                                                        ChannelWebhook webhook = ChannelWebhook.of(
                                                                webhookData.id().asLong(),
                                                                webhookData.token().get(),
                                                                threadId
                                                        );
                                                        this.adminRegistry.setWebhook(channelId, webhook);
                                                        try {
                                                            this.adminRegistry.save();
                                                        } catch (IOException e) {
                                                            Log.logger.error("Failed to save channels during webhook creation batch", e);
                                                        }
                                                        Log.logger.info("Created webhook for " + channelId);
                                                    }))))
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
