package net.teamfruit.eewbot.i18n;

import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.EEWBot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class I18n {

    private static final String FALLBACK_LANGUAGE = "ja_jp";

    private Map<String, String> languages;
    public String defaultLanguage;

    private final Map<String, Map<String, String>> langMap = new HashMap<>();

    public I18n() {
    }

    public void init(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;

        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(I18n.class.getResourceAsStream("/lang/languages.json")))) {
            Map<String, String> languages = EEWBot.GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
            }.getType());

            languages.keySet().forEach(key -> {
                try (InputStreamReader langReader = new InputStreamReader(Objects.requireNonNull(I18n.class.getResourceAsStream("/lang/" + key + ".json")))) {
                    Map<String, String> langMap = EEWBot.GSON.fromJson(langReader, new TypeToken<Map<String, String>>() {
                    }.getType());
                    this.langMap.put(key.toLowerCase(), langMap);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load: " + key + ".json", e);
                }
            });

            this.languages = languages.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load language.json: ", e);
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
