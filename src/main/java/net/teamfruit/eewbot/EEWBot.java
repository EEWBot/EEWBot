package net.teamfruit.eewbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.channel.TextChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.thread.ThreadChannelDeleteEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.shard.ShardingStrategy;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.JsonRegistry;
import net.teamfruit.eewbot.registry.channel.*;
import net.teamfruit.eewbot.registry.config.Config;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.slashcommand.SlashCommandHandler;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class EEWBot {
    public static EEWBot instance;

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
            .create();
    public static final Gson GSON_PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static final ObjectMapper XML_MAPPER = XmlMapper.builder().addModule(new JavaTimeModule()).build();

    public static final String DATA_DIRECTORY = System.getenv("DATA_DIRECTORY");
    public static final String CONFIG_DIRECTORY = System.getenv("CONFIG_DIRECTORY");

    private final JsonRegistry<ConfigV2> config = new JsonRegistry<>(getConfigPath(), ConfigV2::new, ConfigV2.class, GSON_PRETTY);
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "eewbot-worker"));
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private GatewayDiscordClient gateway;
    private ChannelRegistry channels;
    private I18n i18n;
    private QuakeInfoStore quakeInfoStore;
    private RendererQueryFactory rendererQueryFactory;
    private EEWService service;
    private EEWExecutor executor;
    private SlashCommandHandler slashCommand;
    private ExternalWebhookService externalWebhookService;

    private long applicationId;
    private String userName;
    private String avatarUrl;

    public void initialize() throws IOException {
        try {
            this.config.init(true);
        } catch (JsonParseException e) {
            JsonRegistry<Config> oldConfig = new JsonRegistry<>(getConfigPath(), Config::new, Config.class, GSON_PRETTY);
            oldConfig.load(false);
            this.config.setElement(ConfigV2.fromV1(oldConfig.getElement()));
            this.config.save();
        }

        this.i18n = new I18n(getConfig().getBase().getDefaultLanguage());
        this.rendererQueryFactory = new RendererQueryFactory(getConfig().getRenderer().getAddress(), getConfig().getRenderer().getKey());

        Path path = DATA_DIRECTORY != null ? Paths.get(DATA_DIRECTORY, "channels.json") : Paths.get("channels.json");
        if (StringUtils.isNotEmpty(getConfig().getRedis().getAddress())) {
            String redisAddress = getConfig().getRedis().getAddress();
            HostAndPort hnp = redisAddress.lastIndexOf(":") < 0 ? new HostAndPort(redisAddress, 6379) : HostAndPort.from(redisAddress);
            JedisPooled jedisPooled = new JedisPooled(hnp);
            ChannelRegistryRedis registry = new ChannelRegistryRedis(jedisPooled, GSON);
            registry.init(() -> new ChannelRegistryJson(path, GSON));
            this.channels = registry;
        } else {
            ChannelRegistryJson registry = new ChannelRegistryJson(path, GSON);
            registry.init(false);
            this.channels = registry;
        }

        final String token = System.getenv("TOKEN");
        if (token != null)
            getConfig().getBase().setDiscordToken(token);

        String dmdataAPIKey = System.getenv("DMDATA_API_KEY");
        if (dmdataAPIKey != null)
            getConfig().getDmdata().setAPIKey(dmdataAPIKey);

        if (!getConfig().isValid()) {
            return;
        }

        this.gateway = DiscordClient.create(getConfig().getBase().getDiscordToken())
                .gateway()
                .setSharding(ShardingStrategy.recommended())
                .setEnabledIntents(IntentSet.of(Intent.GUILDS))
                .login()
                .block();

        if (this.gateway == null)
            throw new RuntimeException("Discordへの接続に失敗しました。");

        int shardCount = this.gateway.getGatewayClientGroup().getShardCount();

        this.gateway.on(ReadyEvent.class)
                .map(event -> {
                    int count = event.getGuilds().size();
                    Log.logger.info("Connecting {} guilds...", count);
                    return count;
                })
                .take(shardCount)
                .reduce(0, Integer::sum)
                .subscribe(sum -> Log.logger.info("Connected to {} shard(s), {} guild(s)!", shardCount, sum));

        this.applicationId = this.gateway.getSelfId().asLong();

        final User self = this.gateway.getSelf().block();
        if (self != null) {
            this.userName = self.getUsername();
            this.avatarUrl = self.getAvatarUrl();

            Log.logger.info("BotUser: {}", this.userName);
        } else {
            Log.logger.error("Failed to get bot user");
            return;
        }

        this.quakeInfoStore = new QuakeInfoStore();
        this.service = new EEWService(this);
        this.externalWebhookService = new ExternalWebhookService(getConfig(), getHttpClient());
        this.executor = new EEWExecutor(getService(), getConfig(), getApplicationId(), this.scheduledExecutor, getClient(), getChannels(), getQuakeInfoStore(), getExternalWebhookService());
        this.slashCommand = new SlashCommandHandler(this);

        this.executor.init();

        if (this.channels.isGuildEmpty()) {
            Log.logger.info("Registering guild ids");
            this.gateway.getGuilds().flatMap(Guild::getChannels)
                    .subscribe(channel -> {
                                long channelId = channel.getId().asLong();
                                if (this.channels.exists(channelId)) {
                                    this.channels.setGuildId(channel.getId().asLong(), channel.getGuildId().asLong());
                                    this.channels.setIsGuild(channelId, true);
                                }
                            },
                            e -> Log.logger.error("Failed to register guild ids", e),
                            () -> {
                                this.channels.actionOnChannels(ChannelFilter.builder().isGuild(null).build(),
                                        channelId -> this.channels.setIsGuild(channelId, false));
                                try {
                                    this.channels.save();
                                    Log.logger.info("Registered guild ids");
                                } catch (IOException e) {
                                    Log.logger.error("Failed to save channels", e);
                                }
                            });
        }

        this.gateway.on(GuildDeleteEvent.class)
                .subscribe(event -> handleDeletion(event.getGuildId().asLong(), true));
        this.gateway.on(TextChannelDeleteEvent.class)
                .subscribe(event -> handleDeletion(event.getChannel().getId().asLong(), false));
        this.gateway.on(ThreadChannelDeleteEvent.class)
                .subscribe(event -> handleDeletion(event.getChannel().getId().asLong(), false));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.logger.info("Shutdown");
            try {
                getChannels().save();
            } catch (final IOException e) {
                Log.logger.error("Save failed", e);
            }
            if (this.externalWebhookService != null) {
                this.externalWebhookService.shutdown();
            }
        }));

        this.gateway.onDisconnect().block();
    }

    private void handleDeletion(long id, boolean isGuild) {
        if (isGuild)
            this.channels.actionOnChannels(ChannelFilter.builder().guildId(id).build(), this.channels::remove);
        else
            this.channels.remove(id);
        try {
            this.channels.save();
        } catch (IOException e) {
            Log.logger.error("Failed to save channels", e);
        }
    }

    public ConfigV2 getConfig() {
        return this.config.getElement();
    }

    public I18n getI18n() {
        return this.i18n;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return this.scheduledExecutor;
    }

    public ChannelRegistry getChannels() {
        return this.channels;
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    public GatewayDiscordClient getClient() {
        return this.gateway;
    }

    public QuakeInfoStore getQuakeInfoStore() {
        return this.quakeInfoStore;
    }

    public RendererQueryFactory getRendererQueryFactory() {
        return this.rendererQueryFactory;
    }

    public EEWService getService() {
        return this.service;
    }

    public EEWExecutor getExecutor() {
        return this.executor;
    }

    public long getApplicationId() {
        return this.applicationId;
    }

    public String getUsername() {
        return this.userName;
    }

    public String getAvatarUrl() {
        return this.avatarUrl;
    }

    public ExternalWebhookService getExternalWebhookService() {
        return this.externalWebhookService;
    }

    private static Path getConfigPath() {
        return CONFIG_DIRECTORY != null ? Paths.get(CONFIG_DIRECTORY, "config.json") : Paths.get("config.json");
    }

    public static void main(final String[] args) throws Exception {
        instance = new EEWBot();
        instance.initialize();
    }
}
