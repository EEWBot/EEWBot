package net.teamfruit.eewbot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher;
import net.teamfruit.eewbot.dispatcher.NTPDispatcher;
import net.teamfruit.eewbot.dispatcher.QuakeInfoDispather;
import net.teamfruit.eewbot.gui.EEWBotGui;
import sx.blah.discord.Discord4J.Discord4JLogger;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;

public class EEWBot {
	public static EEWBot instance;

	public static final Logger LOGGER = new Discord4JLogger(EEWBot.class.getName());
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
		@Override
		public Thread newThread(final Runnable r) {
			return new Thread(r, "EEWBot-communication-thread");
		}
	});
	private Config config = new Config();
	private final Map<Long, CopyOnWriteArrayList<Channel>> channels = new ConcurrentHashMap<>();
	private Map<String, Permission> permissions = new HashMap<String, Permission>() {
		{
			put("owner", Permission.ALL);
			put("everyone", Permission.DEFAULT_EVERYONE);
		}
	};
	private IDiscordClient client;

	public EEWBot() throws Exception {
		loadConfigs();
		if (this.config.isDebug())
			((Discord4JLogger) EEWBot.LOGGER).setLevel(Discord4JLogger.Level.DEBUG);

		if (StringUtils.isEmpty(this.config.getToken())) {
			EEWBot.LOGGER.info("Please set a token");
			return;
		}

		this.client = createClient(this.config.getToken(), true);
		final EventDispatcher dispatcher = this.client.getDispatcher();
		dispatcher.registerListener(new DiscordEventListener());
		dispatcher.registerListener(new EEWEventListener());

		this.executor.scheduleAtFixedRate(NTPDispatcher.INSTANCE, 0, this.config.getTimeFixDelay()>=3600 ? this.config.getTimeFixDelay() : 3600, TimeUnit.SECONDS);
		this.executor.scheduleAtFixedRate(EEWDispatcher.INSTANCE, 10, this.config.getKyoshinDelay()>=1 ? this.config.getKyoshinDelay() : 1, TimeUnit.SECONDS);
		this.executor.scheduleAtFixedRate(QuakeInfoDispather.INSTANCE, 0, this.config.getQuakeInfoDelay()>=10 ? this.config.getQuakeInfoDelay() : 10, TimeUnit.SECONDS);
		EEWBot.LOGGER.info("Hello");
	}

	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}

	public Config getConfig() {
		return this.config;
	}

	public Map<Long, CopyOnWriteArrayList<Channel>> getChannels() {
		return this.channels;
	}

	public Map<String, Permission> getPermissions() {
		return this.permissions;
	}

	public IDiscordClient getClient() {
		return this.client;
	}

	public static void main(final String[] args) throws Exception {
		if (args.length>0) {
			final Stream<String> stream = Stream.of(args);
			if (!stream.anyMatch(arg -> arg.equals("nogui")))
				new EEWBotGui().setVisible(true);
		}
		instance = new EEWBot();
	}

	public static IDiscordClient createClient(final String token, final boolean login) {
		final ClientBuilder clientBuilder = new ClientBuilder();
		clientBuilder.withToken(token);
		if (login)
			return clientBuilder.login();
		else
			return clientBuilder.build();
	}

	public void loadConfigs() throws ConfigException {
		try {
			final Path cfgPath = Paths.get("config.json");
			if (!cfgPath.toFile().exists()) {
				try (Writer w = Files.newBufferedWriter(cfgPath)) {
					EEWBot.GSON.toJson(this.config, w);
				}
			} else {
				try (Reader r = Files.newBufferedReader(cfgPath)) {
					final Config c = EEWBot.GSON.fromJson(r, Config.class);
					if (c!=null)
						this.config = c;
				}
				try (Writer w = Files.newBufferedWriter(cfgPath)) {
					EEWBot.GSON.toJson(new Config().set(this.config), w);
				}
			}

			final Path channelPath = Paths.get("channels.json");
			if (!channelPath.toFile().exists()) {
				try (Writer w = Files.newBufferedWriter(channelPath)) {
					EEWBot.GSON.toJson(new ConcurrentHashMap<Long, CopyOnWriteArrayList<Channel>>(), w);
				}
			} else {
				try (Reader r = Files.newBufferedReader(channelPath)) {
					final Type type = new TypeToken<Map<Long, Collection<Channel>>>() {
					}.getType();
					final Map<Long, Collection<Channel>> map = EEWBot.GSON.fromJson(r, type);
					if (map!=null)
						for (final Entry<Long, Collection<Channel>> entry : map.entrySet())
							this.channels.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
				}
			}

			final Type type = new TypeToken<Map<String, Permission>>() {
			}.getType();
			final Path permissionPath = Paths.get("permission.json");
			if (!permissionPath.toFile().exists()) {
				try (Writer w = Files.newBufferedWriter(permissionPath)) {
					EEWBot.GSON.toJson(this.permissions, type, w);
				}
			} else {
				try (Reader r = Files.newBufferedReader(permissionPath)) {
					final Map<String, Permission> permissions = EEWBot.GSON.fromJson(r, type);
					if (permissions!=null&&!permissions.isEmpty())
						this.permissions = permissions;
				}
			}
		} catch (JsonSyntaxException|JsonIOException|IOException e) {
			throw new ConfigException("Config load error", e);
		}
	}

	public void saveConfigs() throws ConfigException {
		try {
			final Path cfgPath = Paths.get("config.json");
			try (Writer w = Files.newBufferedWriter(cfgPath)) {
				EEWBot.GSON.toJson(this.config, w);
			}
			final Path channelPath = Paths.get("channels.json");
			try (Writer w = Files.newBufferedWriter(channelPath)) {
				EEWBot.GSON.toJson(this.channels, w);
			}
			//			final Path permissionPath = Paths.get("permission.json");
			//			try (Writer w = Files.newBufferedWriter(permissionPath)) {
			//				EEWBot.GSON.toJson(this.permissions, w);
			//			}
		} catch (JsonIOException|IOException e) {
			throw new ConfigException("Config save error", e);
		}
	}
}
