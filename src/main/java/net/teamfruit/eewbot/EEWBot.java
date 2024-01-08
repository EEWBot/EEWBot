package net.teamfruit.eewbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.shard.ShardingStrategy;
import discord4j.gateway.intent.IntentSet;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.*;
import net.teamfruit.eewbot.slashcommand.SlashCommandHandler;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class EEWBot {
    public static EEWBot instance;

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
            .create();

    public static final String DATA_DIRECTORY = System.getenv("DATA_DIRECTORY");
    public static final String CONDIG_DIRECTORY = System.getenv("CONFIG_DIRECTORY");

    private final ConfigurationRegistry<Config> config = new ConfigurationRegistry<>(CONDIG_DIRECTORY != null ? Paths.get(CONDIG_DIRECTORY, "config.json") : Paths.get("config.json"), Config::new, Config.class);
    private final ChannelRegistry channels = new ChannelRegistry(DATA_DIRECTORY != null ? Paths.get(DATA_DIRECTORY, "channels.json") : Paths.get("channels.json"));

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "eewbot-worker"));

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private GatewayDiscordClient gateway;
    private EEWService service;
    private EEWExecutor executor;
    private SlashCommandHandler slashCommand;

    private long applicationId;
    private String userName;
    private String avatarUrl;
    private Optional<TextChannel> systemChannel;

    public void initialize() throws IOException {
        this.config.init();

        if (StringUtils.isNotEmpty(getConfig().getRedisAddress())) {
            String redisAddress = getConfig().getRedisAddress();
            HostAndPort hnp = redisAddress.lastIndexOf(":") < 0 ? new HostAndPort(redisAddress, 6379) : HostAndPort.from(redisAddress);
            JedisPooled jedisPooled = new JedisPooled(hnp);
            this.channels.init(jedisPooled);
        } else {
            this.channels.init();
        }

        I18n.INSTANCE.init();

        final String token = System.getenv("TOKEN");
        if (token != null)
            getConfig().setToken(token);

        String dmdataAPIKey = System.getenv("DMDATA_API_KEY");
        if (dmdataAPIKey != null)
            getConfig().setDmdataAPIKey(dmdataAPIKey);

        if (!getConfig().validate()) {
            return;
        }

        this.gateway = DiscordClient.create(getConfig().getToken())
                .gateway()
                .setSharding(ShardingStrategy.recommended())
                .setEnabledIntents(IntentSet.none())
//				.setInitialPresence(s -> ClientPresence.online(ClientActivity.playing("!eew help")))
                .login()
                .block();

        if (this.gateway == null)
            throw new RuntimeException("Discordへの接続に失敗しました。");

        int shardCount = this.gateway.getGatewayClientGroup().getShardCount();

        int guildCount = this.gateway.on(ReadyEvent.class)
                .map(event -> {
                    int count = event.getGuilds().size();
                    Log.logger.info("Connecting {} guilds...", count);
                    return count;
                })
                .take(shardCount)
                .reduce(0, Integer::sum)
                .block();

        Log.logger.info("Connected to {} shard(s), {} guild(s)!", shardCount, guildCount);

        this.applicationId = this.gateway.getRestClient().getApplicationId().block();

        final User self = this.gateway.getSelf().block();
        this.userName = self.getUsername();
        this.avatarUrl = self.getAvatarUrl();

        Log.logger.info("BotUser: {}", this.userName);

//        if (StringUtils.isEmpty(getConfig().getSystemChannel())) {
//            this.systemChannel = Optional.ofNullable(this.gateway.getGuilds()
//                    .filter(guild -> self.getId().equals(guild.getId()))
//                    .next()
//                    .flatMap(guild -> guild.getChannels()
//                            .filter(c -> c.getName().equals("monitor"))
//                            .next())
//                    .cast(TextChannel.class)
//                    .switchIfEmpty(this.gateway.getGuilds()
//                            .count()
//                            .filter(count -> count < 10)
//                            .flatMap(count -> this.gateway.createGuild(spec -> spec.setName("EEWBot System")
//                                            .setRegion(Region.Id.JAPAN)
//                                            .addChannel("monitor", discord4j.core.object.entity.channel.Channel.Type.GUILD_TEXT))
//                                    .flatMap(g -> g.getChannels()
//                                            .filter(c -> c.getName().equals("monitor"))
//                                            .next()
//                                            .cast(TextChannel.class))))
//                    .block());
//        } else {
//            this.systemChannel = Optional.ofNullable(this.gateway.getChannelById(Snowflake.of(getConfig().getSystemChannel()))
//                    .cast(TextChannel.class)
//                    .doOnError(err -> {
//                        Log.logger.error("SystemChannelを正常に取得出来ませんでした: " + getConfig().getSystemChannel());
//                    })
//                    .block());
//        }

//        this.systemChannel.ifPresent(channel -> Log.logger.info("System Guild: " + channel.getGuildId().asString() + " System Channel: " + channel.getId().asString()));

        this.service = new EEWService(this);
        this.executor = new EEWExecutor(getService(), getConfig(), getApplicationId(), this.scheduledExecutor, getClient(), getChannels());
        this.slashCommand = new SlashCommandHandler(this);

        this.executor.init();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.logger.info("Shutdown");
            try {
                getChannels().save();
            } catch (final IOException e) {
                Log.logger.error("Save failed", e);
            }
        }));

        this.gateway.onDisconnect().block();
    }

    public Config getConfig() {
        return this.config.getElement();
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return this.scheduledExecutor;
    }

    public ConfigurationRegistry<Config> getConfigRegistry() {
        return this.config;
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

    public EEWService getService() {
        return this.service;
    }

    public EEWExecutor getExecutor() {
        return this.executor;
    }

    public SlashCommandHandler getSlashCommandHandler() {
        return this.slashCommand;
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

    public Optional<TextChannel> getSystemChannel() {
        return this.systemChannel;
    }

    public static void main(final String[] args) throws Exception {
        instance = new EEWBot();
        instance.initialize();
    }
}
