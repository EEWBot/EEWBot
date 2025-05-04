package net.teamfruit.eewbot.i18n;

import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class I18n {

    private static final String FALLBACK_LANGUAGE = "ja_jp";

    public final String defaultLanguage;

    private Map<String, String> languages;
    private final Map<String, Map<String, String>> langMap = new HashMap<>();

    public I18n(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage.toLowerCase();
        init();
    }

    private void init() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(I18n.class.getResourceAsStream("/lang/languages.json")))) {
            this.languages = EEWBot.GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
            }.getType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load language.json: ", e);
        }

        if (this.languages == null) {
            throw new RuntimeException("Failed to load language.json: languages is null");
        }

        this.languages.keySet().forEach(this::loadLanguage);
        this.languages = this.languages.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue));
    }

    private void loadLanguage(String lang) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL resourceUrl = cl.getResource("lang/" + lang);
        if (resourceUrl == null) {
            throw new IllegalArgumentException("Language directory not found: " + lang);
        }

        String protocol = resourceUrl.getProtocol();
        try {
            if ("file".equals(protocol)) {
                Path path = Paths.get(resourceUrl.toURI());
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
                    for (Path file : ds) {
                        Log.logger.info("Loading language file: " + file);
                        try (InputStreamReader isr = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                            Map<String, String> langMap = EEWBot.GSON.fromJson(isr, new TypeToken<Map<String, String>>() {
                            }.getType());
                            this.langMap.put(lang.toLowerCase(), langMap);
                        } catch (IOException e) {
                            Log.logger.error("Failed to load language file: " + file, e);
                        }
                    }
                }
            } else if ("jar".equals(protocol)) {
                JarURLConnection connection = (JarURLConnection) resourceUrl.openConnection();
                try (JarFile jar = connection.getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    String prefix = "lang/" + lang + "/";
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".json")) {
                            Log.logger.info("Loading language file: " + name);
                            try (InputStreamReader isr = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                                Map<String, String> langMap = EEWBot.GSON.fromJson(isr, new TypeToken<Map<String, String>>() {
                                }.getType());
                                this.langMap.put(lang.toLowerCase(), langMap);
                            } catch (IOException e) {
                                Log.logger.error("Failed to load language file: " + name, e);
                            }
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to load resource: " + e);
        }
    }

    public Map<String, String> getLanguages() {
        return this.languages;
    }

    public String get(final String lang, final String key) {
        return this.langMap.get(lang).getOrDefault(key, this.langMap.get(FALLBACK_LANGUAGE).getOrDefault(key, key));
    }

    public String format(final String lang, final String key, final Object... args) {
        final String text = this.langMap.get(lang).getOrDefault(key, this.langMap.get(FALLBACK_LANGUAGE).get(key));
        if (text != null)
            return String.format(text, args);
        return key;
    }
}
