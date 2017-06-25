package net.teamfruit.eewbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;

public class EEWBot {

	public static final File JARPATH = new File(".").getAbsoluteFile();
	public static final Gson GSON = new Gson();
	public static Config config;
	public static IDiscordClient client;

	public static void main(final String[] args) throws ConfigException {
		try {
			config = GSON.fromJson(new BufferedReader(new FileReader(new File(JARPATH, "config.json"))), Config.class);
		} catch (JsonSyntaxException|JsonIOException|FileNotFoundException e) {
			throw new ConfigException(e);
		}
		client = createClient(config.token, true);

	}

	public static IDiscordClient createClient(final String token, final boolean login) {
		final ClientBuilder clientBuilder = new ClientBuilder();
		clientBuilder.withToken(token);
		if (login)
			return clientBuilder.login();
		else
			return clientBuilder.build();
	}
}
