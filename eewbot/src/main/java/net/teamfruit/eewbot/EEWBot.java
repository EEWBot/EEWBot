package net.teamfruit.eewbot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher;
import net.teamfruit.eewbot.dispatcher.NTPDispatcher;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;

public class EEWBot {

	public static final Logger LOGGER = LoggerFactory.getLogger(EEWBot.class);
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final File JARPATH = new File(".").getAbsoluteFile();
	public static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
		@Override
		public Thread newThread(final Runnable r) {
			return new Thread(r, "EEWBot-communication-thread");
		}
	});

	public static Config config;
	public static Map<Long, Collection<Channel>> channels;
	public static NTPDispatcher ntp;
	public static IDiscordClient client;

	public static void main(final String[] args) throws ConfigException {
		loadConfigs();
		client = createClient(config.token, true);
		final EventDispatcher dispatcher = client.getDispatcher();
		dispatcher.registerListener(new DiscordEventListener());

		executor.scheduleAtFixedRate(ntp = new NTPDispatcher(), 0, EEWBot.config.timeFixDelay>=3600 ? EEWBot.config.timeFixDelay : 3600, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(new EEWDispatcher(), 10, config.kyoshinDelay, TimeUnit.SECONDS);
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
			if (!cfgFile.exists())
				GSON.toJson(Config.getDefault(), new BufferedWriter(new FileWriter(cfgFile)));
			config = GSON.fromJson(new BufferedReader(new FileReader(cfgFile)), Config.class);

			final File channelFile = new File(JARPATH, "channels.json");
			final Type type = new TypeToken<Map<Long, Collection<Long>>>() {
			}.getType();
			if (!channelFile.exists())
				GSON.toJson(new HashMap<Long, Collection<Channel>>(), new BufferedWriter(new FileWriter(channelFile)));
			channels = GSON.fromJson(new BufferedReader(new FileReader(channelFile)), type);
		} catch (JsonSyntaxException|JsonIOException|IOException e) {
			throw new ConfigException("Config load error", e);
		}
	}

	public static void saveConfigs() throws ConfigException {
		try {
			final File cfgFile = new File(JARPATH, "config.json");
			GSON.toJson(Config.getDefault(), new BufferedWriter(new FileWriter(cfgFile)));

			final File channelFile = new File(JARPATH, "channels.json");
			final Type type = new TypeToken<Map<Long, Collection<Channel>>>() {
			}.getType();
			GSON.toJson(channels, new BufferedWriter(new FileWriter(channelFile)));
		} catch (JsonIOException|IOException e) {
			throw new ConfigException("Config save error", e);
		}
	}
}
