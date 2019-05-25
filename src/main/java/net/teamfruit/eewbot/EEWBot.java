package net.teamfruit.eewbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import net.teamfruit.eewbot.command.CommandHandler;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.Config;
import net.teamfruit.eewbot.registry.ConfigurationRegistry;
import net.teamfruit.eewbot.registry.Guild;
import net.teamfruit.eewbot.registry.Permission;
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

	private final RequestConfig reqest = RequestConfig.custom()
			.setConnectTimeout(1000*10)
			.setSocketTimeout(10000*10)
			.build();
	private final PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
	private final CloseableHttpClient http = HttpClientBuilder.create().setDefaultRequestConfig(this.reqest)
			.setDefaultHeaders(Arrays.asList(new BasicHeader(HttpHeaders.ACCEPT_CHARSET, "UTF-8")))
			.setConnectionManager(this.manager)
			.build();

	private DiscordClient client;
	private EEWService service;
	private EEWExecutor executor;
	private CommandHandler command;

	private String userName;
	private String avatarUrl;

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

		this.client = new DiscordClientBuilder(getConfig().getToken()).build();

		this.client.getEventDispatcher().on(ReadyEvent.class)
				.subscribe(event -> Log.logger.info("Connecting {} guilds...", event.getGuilds().size()));

		this.service = new EEWService(this.client, getChannels());
		this.executor = new EEWExecutor(this.service, getConfig());
		this.command = new CommandHandler(this);

		this.client.getEventDispatcher().on(ReadyEvent.class)
				.map(event -> event.getGuilds().size())
				.flatMap(size -> this.client.getEventDispatcher()
						.on(GuildCreateEvent.class)
						.take(size)
						.collectList())
				.subscribe(events -> {
					this.executor.init();
					this.client.getSelf().subscribe(user -> {
						this.userName = user.getUsername();
						this.avatarUrl = user.getAvatarUrl();
					});

					Log.logger.info("Connected!");
				});

		this.client.getEventDispatcher().on(GuildCreateEvent.class)
				.map(e -> e.getGuild().getId().asLong())
				.filter(l -> !getGuilds().containsKey(l))
				.flatMap(l -> Mono.fromCallable(() -> {
					getGuilds().put(l, new Guild().setLang(getConfig().getDefaultLanuage()));
					EEWBot.this.guilds.save();
					return l;
				}))
				.doOnError(err -> Log.logger.error("guilds.jsonのセーブに失敗しました", err))
				.subscribe();

		this.client.login().block();
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

	public DiscordClient getClient() {
		return this.client;
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

	public String getUsername() {
		return this.userName;
	}

	public String getAvatarUrl() {
		return this.avatarUrl;
	}

	public static void main(final String[] args) throws Exception {
		instance = new EEWBot();
		instance.initialize();
	}
}
