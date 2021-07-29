package net.teamfruit.eewbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.Region;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import net.teamfruit.eewbot.command.CommandHandler;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Config;
import net.teamfruit.eewbot.registry.ConfigurationRegistry;
import net.teamfruit.eewbot.registry.Guild;
import net.teamfruit.eewbot.registry.Permission;
import net.teamfruit.eewbot.slashcommand.SlashCommandHandler;
import reactor.core.publisher.Mono;

public class EEWBot {
	public static EEWBot instance;

	@SuppressWarnings("deprecation")
	public static final Gson GSON = new GsonBuilder()
			.registerTypeAdapter(net.teamfruit.eewbot.registry.OldChannel.class, new net.teamfruit.eewbot.registry.OldChannel.ChannelTypeAdapter())
			.create();

	public static final String DATA_DIRECTORY = System.getenv("DATA_DIRECTORY");
	public static final String CONDIG_DIRECTORY = System.getenv("CONFIG_DIRECTORY");

	private final ConfigurationRegistry<Config> config = new ConfigurationRegistry<>(CONDIG_DIRECTORY!=null ? Paths.get(CONDIG_DIRECTORY, "config.json") : Paths.get("config.json"), () -> new Config(), Config.class);
	private final ConfigurationRegistry<Map<Long, Channel>> channels = new ConfigurationRegistry<>(DATA_DIRECTORY!=null ? Paths.get(DATA_DIRECTORY, "channels.json") : Paths.get("channels.json"), () -> new ConcurrentHashMap<Long, Channel>(), new TypeToken<Map<Long, Channel>>() {
	}.getType());
	private final ConfigurationRegistry<Map<String, Permission>> permissions = new ConfigurationRegistry<>(CONDIG_DIRECTORY!=null ? Paths.get(CONDIG_DIRECTORY, "permission.json") : Paths.get("permission.json"), () -> new HashMap<String, Permission>() {
		{
			put("owner", Permission.ALL);
			put("everyone", Permission.DEFAULT_EVERYONE);
		}
	}, new TypeToken<Map<String, Permission>>() {
	}.getType());
	private final ConfigurationRegistry<Map<Long, Guild>> guilds = new ConfigurationRegistry<>(DATA_DIRECTORY!=null ? Paths.get(DATA_DIRECTORY, "guilds.json") : Paths.get("guilds.json"), () -> new ConcurrentHashMap<Long, Guild>(), new TypeToken<Map<Long, Guild>>() {
	}.getType());

	private final ReentrantReadWriteLock channelsLock = new ReentrantReadWriteLock();

	private final RequestConfig reqest = RequestConfig.custom()
			.setConnectTimeout(1000*10)
			.setSocketTimeout(10000*10)
			.build();
	private final PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
	private final CloseableHttpClient http = HttpClientBuilder.create().setDefaultRequestConfig(this.reqest)
			.setDefaultHeaders(Arrays.asList(new BasicHeader(HttpHeaders.ACCEPT_CHARSET, "UTF-8")))
			.setConnectionManager(this.manager)
			.build();

	private GatewayDiscordClient gateway;
	private EEWService service;
	private EEWExecutor executor;
	private CommandHandler command;
	private SlashCommandHandler slashCommand;

	private String userName;
	private String avatarUrl;
	private Optional<TextChannel> systemChannel;

	public void initialize() throws IOException {
		this.config.init();
		initChannels();
		this.permissions.init();
		I18n.INSTANCE.init();
		this.guilds.init();

		final String token = System.getenv("TOKEN");
		if (token!=null)
			getConfig().setToken(token);

		if (StringUtils.isEmpty(getConfig().getToken())) {
			Log.logger.info("Please set a token");
			return;
		}

		this.gateway = DiscordClient.create(getConfig().getToken()).login().block();

		this.gateway.on(GuildCreateEvent.class)
				.map(e -> e.getGuild().getId().asLong())
				.filter(l -> !getGuilds().containsKey(l))
				.flatMap(l -> Mono.fromCallable(() -> {
					getGuilds().put(l, new Guild().setLang(getConfig().getDefaultLanuage()));
					EEWBot.this.guilds.save();
					return l;
				}))
				.doOnError(err -> Log.logger.error("guilds.jsonのセーブに失敗しました", err))
				.subscribe();

		final List<GuildCreateEvent> events = this.gateway.on(ReadyEvent.class)
				.map(event -> {
					Log.logger.info("Connecting {} guilds...", event.getGuilds().size());
					return event.getGuilds().size();
				})
				.flatMap(size -> this.gateway
						.on(GuildCreateEvent.class)
						.take(size)
						.collectList())
				.blockFirst();

		Log.logger.info("Connected to {} guilds!", events.size());

		final User self = this.gateway.getSelf().block();
		this.userName = self.getUsername();
		this.avatarUrl = self.getAvatarUrl();

		Log.logger.info("BotUser: {}", this.userName);

		final Optional<discord4j.core.object.entity.Guild> guild = events.stream().map(GuildCreateEvent::getGuild)
				.filter(g -> g.getOwnerId().equals(self.getId()))
				.findFirst();
		if (!guild.isPresent()) {
			if (StringUtils.isNotEmpty(getConfig().getSystemChannel()))
				this.systemChannel = Optional.of(this.gateway.getChannelById(Snowflake.of(getConfig().getSystemChannel()))
						.cast(TextChannel.class)
						.doOnError(err -> {
							Log.logger.error("SystemChannelを正常に取得出来ませんでした: "+getConfig().getSystemChannel());
							this.systemChannel = Optional.empty();
						})
						.block());
			else if (events.size()<10)
				this.systemChannel = Optional.of(this.gateway.createGuild(spec -> spec.setName("EEWBot System").setRegion(Region.Id.JAPAN).addChannel("monitor", discord4j.core.object.entity.channel.Channel.Type.GUILD_TEXT))
						.flatMap(g -> g.getChannels()
								.filter(c -> c.getName().equals("monitor"))
								.last()
								.cast(TextChannel.class))
						.block());
			else
				this.systemChannel = Optional.empty();
		} else
			this.systemChannel = Optional.of(guild.get().getChannels()
					.filter(c -> c.getName().equals("monitor"))
					.last()
					.cast(TextChannel.class)
					.block());

		this.systemChannel.ifPresent(channel -> Log.logger.info("System Guild: "+channel.getGuildId().asString()+" System Channel: "+channel.getId().asString()));

		this.gateway.updatePresence(Presence.online(Activity.playing("!eew help"))).subscribe();

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

			final ConfigurationRegistry<Map<Long, List<net.teamfruit.eewbot.registry.OldChannel>>> oldChannels = new ConfigurationRegistry<>(DATA_DIRECTORY!=null ? Paths.get(DATA_DIRECTORY, "channels.json") : Paths.get("channels.json"),
					() -> new ConcurrentHashMap<Long, List<net.teamfruit.eewbot.registry.OldChannel>>(),
					new TypeToken<Map<Long, Collection<net.teamfruit.eewbot.registry.OldChannel>>>() {
					}.getType());

			Files.copy(oldChannels.getPath(), DATA_DIRECTORY!=null ? Paths.get(DATA_DIRECTORY, "oldchannels.json") : Paths.get("oldchannels.json"));
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

	public Map<Long, Guild> getGuilds() {
		return this.guilds.getElement();
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

	public ConfigurationRegistry<Map<Long, Guild>> getGuildsRegistry() {
		return this.guilds;
	}

	public CloseableHttpClient getHttpClient() {
		return this.http;
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
