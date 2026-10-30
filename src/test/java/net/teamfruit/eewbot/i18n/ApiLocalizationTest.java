package net.teamfruit.eewbot.i18n;

import com.google.gson.JsonObject;
import net.teamfruit.eewbot.Codecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLocalizationTest {

    private static final String PREFIX = "eewbot.scmd.api.";
    private static final I18n I18N = new I18n("ja_jp");

    @Test
    void apiMessagesHaveMatchingJapaneseAndEnglishTranslations() throws IOException {
        JsonObject japanese = resource("ja_JP");
        JsonObject english = resource("en_US");
        Set<String> keys = apiKeys(japanese);
        assertThat(keys).isNotEmpty();
        assertThat(apiKeys(english)).containsExactlyInAnyOrderElementsOf(keys);
        for (String key : keys) {
            assertThat(japanese.get(key).getAsString()).isNotBlank();
            assertThat(english.get(key).getAsString()).isNotBlank();
            assertThat(I18N.get("en_us", key)).isEqualTo(english.get(key).getAsString());
        }
    }

    @ParameterizedTest
    @CsvSource({"zh_tw, zh_TW", "ko_hanja, ko_Hanja", "ko_kp, ko_KP"})
    void otherLanguagesFallBackToJapaneseForEveryApiMessage(String language, String locale) throws IOException {
        JsonObject japanese = resource("ja_JP");
        assertThat(apiKeys(resource(locale))).isEmpty();
        for (String key : apiKeys(japanese)) {
            assertThat(I18N.get(language, key)).isEqualTo(japanese.get(key).getAsString());
        }
    }

    private static Set<String> apiKeys(JsonObject messages) {
        return messages.keySet().stream().filter(key -> key.startsWith(PREFIX)).collect(Collectors.toSet());
    }

    private static JsonObject resource(String locale) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(
                ApiLocalizationTest.class.getResourceAsStream("/lang/" + locale + "/eewbot.json")), StandardCharsets.UTF_8)) {
            return Codecs.GSON.fromJson(reader, JsonObject.class);
        }
    }
}
