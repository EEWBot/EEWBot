package net.teamfruit.eewbot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher;
import net.teamfruit.eewbot.dispatcher.NTPDispatcher;
import sx.blah.discord.Discord4J.Discord4JLogger;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;

public class EEWBot {

	public static final Logger LOGGER = new Discord4JLogger(EEWBot.class.getName());
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final File JARPATH = new File(".").getAbsoluteFile();
	public static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
		@Override
		public Thread newThread(final Runnable r) {
			return new Thread(r, "EEWBot-communication-thread");
		}
	});

	public static Config config = Config.getDefault();
	public static Map<Long, CopyOnWriteArrayList<Channel>> channels = new ConcurrentHashMap<>();
	public static NTPDispatcher ntp;
	public static IDiscordClient client;

	public static void main(final String[] args) throws Exception {
		loadConfigs();
		if (StringUtils.isEmpty(config.token)) {
			LOGGER.info("Please set a token");
			System.exit(0);
		}
		client = createClient(config.token, true);
		final EventDispatcher dispatcher = client.getDispatcher();
		dispatcher.registerListener(new DiscordEventListener());
		dispatcher.registerListener(new EEWEventListener());

		executor.scheduleAtFixedRate(ntp = new NTPDispatcher(), 0, EEWBot.config.timeFixDelay>=3600 ? EEWBot.config.timeFixDelay : 3600, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(new EEWDispatcher(), 10, config.kyoshinDelay>=1 ? config.kyoshinDelay : 1, TimeUnit.SECONDS);
		LOGGER.info("Hello");
	}

	public static IDiscordClient createClient(final String token, final boolean login) {
		final ClientBuilder clientBuilder = new ClientBuilder();
		clientBuilder.withToken(token);
		if (login)
			return clientBuilder.login();
		else
			return clientBuilder.build();
	}

	public static void loadConfigs() throws ConfigException {
		try {
			final File cfgFile = new File(JARPATH, "config.json");
			if (!cfgFile.exists()) {
				final Writer w = new BufferedWriter(new FileWriter(cfgFile));
				GSON.toJson(config, w);
				w.close();
			} else {
				final Config c = GSON.fromJson(new BufferedReader(new FileReader(cfgFile)), Config.class);
				if (c!=null)
					config = c;
			}

			final File channelFile = new File(JARPATH, "channels.json");
			final Type type = new TypeToken<Map<Long, Collection<Long>>>() {
			}.getType();
			if (!channelFile.exists()) {
				final Writer w = new BufferedWriter(new FileWriter(channelFile));
				GSON.toJson(new ConcurrentHashMap<Long, CopyOnWriteArrayList<Channel>>(), w);
				w.close();
			} else {
				final Map<Long, CopyOnWriteArrayList<Channel>> map = GSON.fromJson(new BufferedReader(new FileReader(channelFile)), type);
				if (map!=null)
					channels.putAll(map);
			}
		} catch (JsonSyntaxException|JsonIOException|IOException e) {
			throw new ConfigException("Config load error", e);
		}
	}

	public static void saveConfigs() throws ConfigException {
		try {
			final File cfgFile = new File(JARPATH, "config.json");
			GSON.toJson(Config.getDefault(), new FileWriter(cfgFile));

			final File channelFile = new File(JARPATH, "channels.json");
			final Type type = new TypeToken<Map<Long, Collection<Channel>>>() {
			}.getType();
			GSON.toJson(channels, new BufferedWriter(new FileWriter(channelFile)));
		} catch (JsonIOException|IOException e) {
			throw new ConfigException("Config save error", e);
		}
	}
}
