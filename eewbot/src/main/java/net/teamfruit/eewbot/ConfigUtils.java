package net.teamfruit.eewbot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ConfigUtils {

	public static void writeConfig(final Path path, final Object obj, final Type type) throws IOException {
		try (Writer w = Files.newBufferedWriter(path)) {
			if (type!=null)
				EEWBot.GSON.toJson(obj, type, w);
			else
				EEWBot.GSON.toJson(obj, w);
		}
	}

	public static <T> Optional<T> readConfig(final Path path, final Class<T> clazz) throws IOException {
		try (Reader r = Files.newBufferedReader(path)) {
			return Optional.ofNullable(EEWBot.GSON.fromJson(r, clazz));
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> Optional<T> readConfig(final Path path, final Type type) throws IOException {
		try (Reader r = Files.newBufferedReader(path)) {
			return Optional.ofNullable((T) EEWBot.GSON.fromJson(r, type));
		}
	}
}
