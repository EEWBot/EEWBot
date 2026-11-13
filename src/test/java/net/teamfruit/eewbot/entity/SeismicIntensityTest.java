package net.teamfruit.eewbot.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.teamfruit.eewbot.Codecs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SeismicIntensityTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "UNKNOWN, 0, 不明, 不明, unknown,",
            "ONE, 1, 1, 1, 1, 1",
            "TWO, 2, 2, 2, 2, 2",
            "THREE, 3, 3, 3, 3, 3",
            "FOUR, 4, 4, 4, 4, 4",
            "FIVE_MINUS, 5, 5弱, 5-, 5-, 5-minus",
            "FIVE_PLUS, 6, 5強, 5+, 5+, 5-plus",
            "SIX_MINUS, 7, 6弱, 6-, 6-, 6-minus",
            "SIX_PLUS, 8, 6強, 6+, 6+, 6-plus",
            "SEVEN, 9, 7, 7, 7, 7"
    })
    void iconNamesPreserveExistingSerializationAndParsing(SeismicIntensity intensity, int code, String displayName,
                                                         String symbol, String legacyName, String iconName) throws Exception {
        assertThat(intensity.getIconName()).isEqualTo(iconName);
        assertThat(intensity.getLegacySerializedName()).isEqualTo(legacyName);
        assertThat(SeismicIntensity.get(displayName)).isEqualTo(intensity);
        assertThat(SeismicIntensity.get(symbol)).isEqualTo(intensity);

        String symbolJson = "\"" + symbol + "\"";
        assertThat(JSON_MAPPER.writeValueAsString(intensity)).isEqualTo(symbolJson);
        assertThat(JSON_MAPPER.readValue(symbolJson, SeismicIntensity.class)).isEqualTo(intensity);
        assertThat(Codecs.GSON.toJson(intensity)).isEqualTo(Integer.toString(code));
        assertThat(Codecs.GSON.fromJson(Integer.toString(code), SeismicIntensity.class)).isEqualTo(intensity);
        assertThat(Codecs.GSON.fromJson("\"" + legacyName + "\"", SeismicIntensity.class)).isEqualTo(intensity);
    }
}
