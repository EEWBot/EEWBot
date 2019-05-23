package net.teamfruit.eewbot.i18n;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import net.teamfruit.eewbot.EEWBot;

public class I18n {

	public static final I18n INSTANCE = new I18n();

	private Map<String, String> languages;
	private Map<String, Map<String, String>> langMap = new HashMap<>();

	private I18n() {
	}

	public void init() {
		this.languages = EEWBot.GSON.fromJson(new InputStreamReader(I18n.class.getResourceAsStream("/lang/languages.json")), new TypeToken<Map<String, String>>() {
		}.getType());

		this.languages.keySet().forEach(key -> {
			final Map<String, String> map = EEWBot.GSON.fromJson(new InputStreamReader(I18n.class.getResourceAsStream("/lang/"+key+".json")), new TypeToken<Map<String, String>>() {
			}.getType());

			this.langMap.put(key, map);
		});
	}

	public Map<String, String> getLanguages() {
		return this.languages;
	}

	public String get(final String lang, final String key) {
		final String text = this.langMap.get(lang).get(key);
		if (text!=null)
			return text;
		return key;
	}

	public String format(final String lang, final String key, final Object... args) {
		final String text = this.langMap.get(lang).get(key);
		if (text!=null)
			return String.format(text, args);
		return key;
	}
}
