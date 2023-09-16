package net.teamfruit.eewbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.Region;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.shard.ShardingStrategy;
import discord4j.gateway.intent.IntentSet;
import net.teamfruit.eewbot.command.CommandHandler;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Config;
import net.teamfruit.eewbot.registry.ConfigurationRegistry;
import net.teamfruit.eewbot.registry.Permission;
import net.teamfruit.eewbot.slashcommand.SlashCommandHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class EEWBot {
    public static EEWBot instance;

    @SuppressWarnings("deprecation")
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(net.teamfruit.eewbot.registry.OldChannel.class, new net.teamfruit.eewbot.registry.OldChannel.ChannelTypeAdapter())
            .create();

    public static final String DATA_DIRECTORY = System.getenv("DATA_DIRECTORY");
    public static final String CONDIG_DIRECTORY = System.getenv("CONFIG_DIRECTORY");

    private final ConfigurationRegistry<Config> config = new ConfigurationRegistry<>(CONDIG_DIRECTORY != null ? Paths.get(CONDIG_DIRECTORY, "config.json") : Paths.get("config.json"), () -> new Config(), Config.class);
    private final ConfigurationRegistry<Map<Long, Channel>> channels = new ConfigurationRegistry<>(DATA_DIRECTORY != null ? Paths.get(DATA_DIRECTORY, "channels.json") : Paths.get("channels.json"), () -> new ConcurrentHashMap<Long, Channel>(), new TypeToken<Map<Long, Channel>>() {
    }.getType());
    private final ConfigurationRegistry<Map<String, Permission>> permissions = new ConfigurationRegistry<>(CONDIG_DIRECTORY != null ? Paths.get(CONDIG_DIRECTORY, "permission.json") : Paths.get("permission.json"), () -> new HashMap<String, Permission>() {
        {
            put("owner", Permission.ALL);
            put("everyone", Permission.DEFAULT_EVERYONE);
        }
    }, new TypeToken<Map<String, Permission>>() {
    }.getType());

    private final ReentrantReadWriteLock channelsLock = new ReentrantReadWriteLock();

    private final RequestConfig reqest = RequestConfig.custom()
            .setConnectTimeout(1000 * 10)
            .setSocketTimeout(10000 * 10)
            .build();
    private final PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
    private final CloseableHttpClient apacheHttpClient = HttpClientBuilder.create().setDefaultRequestConfig(this.reqest)
            .setDefaultHeaders(Arrays.asList(new BasicHeader(HttpHeaders.ACCEPT_CHARSET, "UTF-8")))
            .setConnectionManager(this.manager)
            .build();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private GatewayDiscordClient gateway;
    private EEWService service;
    private EEWExecutor executor;
    private CommandHandler command;

    private SlashCommandHandler slashCommand;

    private long applicationId;
    private String userName;
    private String avatarUrl;
    private Optional<TextChannel> systemChannel;

    public void initialize() throws IOException {
        this.config.init();
        initChannels();
        this.permissions.init();
        I18n.INSTANCE.init();

        final String token = System.getenv("TOKEN");
        if (token != null)
            getConfig().setToken(token);

        if (StringUtils.isEmpty(getConfig().getToken())) {
            Log.logger.info("Please set a token");
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

        if (StringUtils.isEmpty(getConfig().getSystemChannel())) {
            this.systemChannel = Optional.ofNullable(this.gateway.getGuilds()
                    .filter(guild -> self.getId().equals(guild.getId()))
                    .next()
                    .flatMap(guild -> guild.getChannels()
                            .filter(c -> c.getName().equals("monitor"))
                            .next())
                    .cast(TextChannel.class)
                    .switchIfEmpty(this.gateway.getGuilds()
                            .count()
                            .filter(count -> count < 10)
                            .flatMap(count -> this.gateway.createGuild(spec -> spec.setName("EEWBot System")
                                            .setRegion(Region.Id.JAPAN)
                                            .addChannel("monitor", discord4j.core.object.entity.channel.Channel.Type.GUILD_TEXT))
                                    .flatMap(g -> g.getChannels()
                                            .filter(c -> c.getName().equals("monitor"))
                                            .next()
                                            .cast(TextChannel.class))))
                    .block());
        } else {
            this.systemChannel = Optional.ofNullable(this.gateway.getChannelById(Snowflake.of(getConfig().getSystemChannel()))
                    .cast(TextChannel.class)
                    .doOnError(err -> {
                        Log.logger.error("SystemChannelを正常に取得出来ませんでした: " + getConfig().getSystemChannel());
                    })
                    .block());
        }

        this.systemChannel.ifPresent(channel -> Log.logger.info("System Guild: " + channel.getGuildId().asString() + " System Channel: " + channel.getId().asString()));

        this.service = new EEWService(getClient(), getChannels(), getChannelsLock(), getSystemChannel());
        this.executor = new EEWExecutor(getService(), getConfig(), getChannelRegistry());
        this.command = new CommandHandler(this);
        this.slashCommand = new SlashCommandHandler(this);

        this.executor.init();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.logger.info("Shutdown");
            try {
                getChannelRegistry().save();
            } catch (final IOException e) {
                Log.logger.error("Save failed", e);
            }
        }));

        this.gateway.onDisconnect().block();
    }

    @SuppressWarnings("deprecation")
    private boolean initChannels() throws IOException {
        try {
            this.channels.init();
            return false;
        } catch (final JsonSyntaxException e) {
            Log.logger.info("Migrating channels.json");

            final ConfigurationRegistry<Map<Long, List<net.teamfruit.eewbot.registry.OldChannel>>> oldChannels = new ConfigurationRegistry<>(DATA_DIRECTORY != null ? Paths.get(DATA_DIRECTORY, "channels.json") : Paths.get("channels.json"),
                    () -> new ConcurrentHashMap<Long, List<net.teamfruit.eewbot.registry.OldChannel>>(),
                    new TypeToken<Map<Long, Collection<net.teamfruit.eewbot.registry.OldChannel>>>() {
                    }.getType());

            Files.copy(oldChannels.getPath(), DATA_DIRECTORY != null ? Paths.get(DATA_DIRECTORY, "oldchannels.json") : Paths.get("oldchannels.json"));
            oldChannels.init();

            final Map<Long, Channel> map = oldChannels.getElement().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(old -> old.id, Channel::fromOldChannel));
            this.channels.setElement(map);
            this.channels.save();
            return true;
        }
    }

    public Config getConfig() {
        return this.config.getElement();
    }

    public Map<Long, Channel> getChannels() {
        return this.channels.getElement();
    }

    public ReentrantReadWriteLock getChannelsLock() {
        return this.channelsLock;
    }

    public Map<String, Permission> getPermissions() {
        return this.permissions.getElement();
    }

    public ConfigurationRegistry<Config> getConfigRegistry() {
        return this.config;
    }

    public ConfigurationRegistry<Map<Long, Channel>> getChannelRegistry() {
        return this.channels;
    }

    public ConfigurationRegistry<Map<String, Permission>> getPermissionsRegistry() {
        return this.permissions;
    }

    @Deprecated
    public CloseableHttpClient getApacheHttpClient() {
        return this.apacheHttpClient;
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

    public CommandHandler getCommandHandler() {
        return this.command;
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
