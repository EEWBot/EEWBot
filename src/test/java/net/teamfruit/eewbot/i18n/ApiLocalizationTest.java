package net.teamfruit.eewbot.i18n;

import com.google.gson.JsonObject;
import net.teamfruit.eewbot.Codecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLocalizationTest {

    private static final List<String> MESSAGE_KEYS = List.of(
            "eewbot.scmd.error.commandsdisabled",
            "eewbot.scmd.error.invalidcontext",
            "eewbot.scmd.error.manageguildrequired",
            "eewbot.scmd.setup.error.start",
            "eewbot.scmd.testmessage.error.start",
            "eewbot.scmd.quakeinfo.error.start",
            "eewbot.scmd.error.unsupported",
            "eewbot.scmd.setup.error.invalidvalue",
            "eewbot.scmd.setup.error.targetmismatch",
            "eewbot.scmd.setup.error.unknown",
            "eewbot.scmd.error.ratelimit",
            "eewbot.scmd.error.missingperms",
            "eewbot.scmd.error.notfound",
            "eewbot.scmd.error.unavailable",
            "eewbot.scmd.error.unsupportedchannel",
            "eewbot.scmd.error.botpermissionsrequired",
            "eewbot.scmd.invite.reply",
            "eewbot.scmd.lang.applyall.followup.count",
            "eewbot.scmd.lang.error.stale",
            "eewbot.scmd.lang.error.invalidvalue",
            "eewbot.scmd.lang.error.unknownlanguage",
            "eewbot.scmd.lang.error.unknown",
            "eewbot.scmd.testmessage.error.configuration",
            "eewbot.scmd.testmessage.error.unavailable",
            "eewbot.scmd.testmessage.error.uncertain",
            "eewbot.scmd.quakeinfo.report",
            "eewbot.scmd.quakeinfo.saved",
            "eewbot.scmd.quakeinfo.old",
            "eewbot.scmd.quakeinfo.partial",
            "eewbot.scmd.quakeinfo.kind.announcement",
            "eewbot.scmd.quakeinfo.kind.correction",
            "eewbot.scmd.quakeinfo.kind.cancel"
    );
    private static final I18n I18N = new I18n("ja_jp");

    @Test
    void apiMessagesHaveMatchingJapaneseAndEnglishTranslations() throws IOException {
        JsonObject japanese = resource("ja_JP");
        JsonObject english = resource("en_US");
        for (String key : MESSAGE_KEYS) {
            assertThat(japanese.has(key)).as(key).isTrue();
            assertThat(english.has(key)).as(key).isTrue();
            assertThat(japanese.get(key).getAsString()).isNotBlank();
            assertThat(english.get(key).getAsString()).isNotBlank();
            assertThat(I18N.get("en_us", key)).isEqualTo(english.get(key).getAsString());
        }
    }

    @ParameterizedTest
    @CsvSource({"zh_tw, zh_TW", "ko_hanja, ko_Hanja", "ko_kp, ko_KP"})
    void otherLanguagesFallBackToJapaneseForEveryApiMessage(String language, String locale) throws IOException {
        JsonObject japanese = resource("ja_JP");
        JsonObject localized = resource(locale);
        for (String key : MESSAGE_KEYS) {
            assertThat(localized.has(key)).as(key).isFalse();
            assertThat(I18N.get(language, key)).isEqualTo(japanese.get(key).getAsString());
        }
    }

    private static JsonObject resource(String locale) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(
                ApiLocalizationTest.class.getResourceAsStream("/lang/" + locale + "/eewbot.json")), StandardCharsets.UTF_8)) {
            return Codecs.GSON.fromJson(reader, JsonObject.class);
        }
    }
}
