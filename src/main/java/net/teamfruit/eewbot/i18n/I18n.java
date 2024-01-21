package net.teamfruit.eewbot.i18n;

import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.EEWBot;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class I18n {

    private static final String FALLBACK_LANGUAGE = "en_us";

    private Map<String, String> languages;
    public String defaultLanguage;

    private final Map<String, Map<String, String>> langMap = new HashMap<>();

    public I18n() {
    }

    public void init(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;

        this.languages = EEWBot.GSON.fromJson(new InputStreamReader(Objects.requireNonNull(I18n.class.getResourceAsStream("/lang/languages.json"))), new TypeToken<Map<String, String>>() {
        }.getType());

        this.languages.keySet().forEach(key -> {
            final Map<String, String> map = EEWBot.GSON.fromJson(new InputStreamReader(Objects.requireNonNull(I18n.class.getResourceAsStream("/lang/" + key + ".json"))), new TypeToken<Map<String, String>>() {
            }.getType());

            this.langMap.put(key, map);
        });
    }

    public Map<String, String> getLanguages() {
        return this.languages;
    }

    public String get(final String lang, final String key) {
        final String text = this.langMap.get(lang).getOrDefault(key, this.langMap.get(FALLBACK_LANGUAGE).get(key));
        if (text != null)
            return text;
        return key;
    }

    public String format(final String lang, final String key, final Object... args) {
        final String text = this.langMap.get(lang).getOrDefault(key, this.langMap.get(FALLBACK_LANGUAGE).get(key));
        if (text != null)
            return String.format(text, args);
        return key;
    }
}
