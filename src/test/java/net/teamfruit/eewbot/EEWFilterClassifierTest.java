package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.AbstractEEW;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE43Impl;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE45Impl;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class EEWFilterClassifierTest {

    private static String readFixture(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                EEWFilterClassifierTest.class.getResourceAsStream("/dmdata/eew/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static EEW parseFixture(String name) throws IOException {
        return parseFixture(name, VXSE45Impl.class);
    }

    private static EEW parseFixture(String name, Class<? extends AbstractEEW> implClass) throws IOException {
        return Codecs.XML_MAPPER.readValue(readFixture(name), implClass);
    }

    @Test
    void classifyEEW_warning_important() {
        ChannelFilter filter = EEWFilterClassifier.classifyEEW(true, true, SeismicIntensity.FIVE_MINUS);

        assertThat(filter.isEewAlertPresent()).isTrue();
        assertThat(filter.getEewAlert()).isTrue();
        assertThat(filter.isEewPredictionPresent()).isFalse();
        assertThat(filter.isEewDecimationPresent()).isFalse();
        assertThat(filter.isIntensityPresent()).isTrue();
        assertThat(filter.getIntensity()).isEqualTo(SeismicIntensity.FIVE_MINUS);
    }

    @Test
    void classifyEEW_prediction_important() {
        ChannelFilter filter = EEWFilterClassifier.classifyEEW(false, true, SeismicIntensity.THREE);

        assertThat(filter.isEewAlertPresent()).isFalse();
        assertThat(filter.isEewPredictionPresent()).isTrue();
        assertThat(filter.getEewPrediction()).isTrue();
        assertThat(filter.isEewDecimationPresent()).isFalse();
        assertThat(filter.getIntensity()).isEqualTo(SeismicIntensity.THREE);
    }

    @Test
    void classifyEEW_warning_notImportant_setsDecimationFalse() {
        ChannelFilter filter = EEWFilterClassifier.classifyEEW(true, false, SeismicIntensity.FOUR);

        assertThat(filter.isEewAlertPresent()).isTrue();
        assertThat(filter.getEewAlert()).isTrue();
        assertThat(filter.isEewDecimationPresent()).isTrue();
        assertThat(filter.getEewDecimation()).isFalse();
    }

    @Test
    void classifyEEW_prediction_notImportant_setsDecimationFalse() {
        ChannelFilter filter = EEWFilterClassifier.classifyEEW(false, false, SeismicIntensity.TWO);

        assertThat(filter.isEewPredictionPresent()).isTrue();
        assertThat(filter.getEewPrediction()).isTrue();
        assertThat(filter.isEewDecimationPresent()).isTrue();
        assertThat(filter.getEewDecimation()).isFalse();
    }

    @Test
    void isDmdataWarning_warningTelegram() throws IOException {
        EEW current = parseFixture("warning_regions.xml", VXSE43Impl.class);

        assertThat(EEWFilterClassifier.isDmdataWarning(current, null)).isTrue();
    }

    @Test
    void isDmdataWarning_forecastTelegram() throws IOException {
        EEW current = parseFixture("forecast_normal.xml");

        assertThat(EEWFilterClassifier.isDmdataWarning(current, null)).isFalse();
    }

    @Test
    void isDmdataWarning_cancel_prevWarning() throws IOException {
        EEW current = parseFixture("cancel.xml");
        EEW prev = parseFixture("warning_regions.xml", VXSE43Impl.class);

        assertThat(EEWFilterClassifier.isDmdataWarning(current, prev)).isTrue();
    }

    @Test
    void isDmdataWarning_cancel_prevForecast() throws IOException {
        EEW current = parseFixture("cancel.xml");
        EEW prev = parseFixture("forecast_normal.xml");

        assertThat(EEWFilterClassifier.isDmdataWarning(current, prev)).isFalse();
    }

    @Test
    void isDmdataWarning_cancel_noPrev() throws IOException {
        EEW current = parseFixture("cancel.xml");

        assertThat(EEWFilterClassifier.isDmdataWarning(current, null)).isFalse();
    }

    @Test
    void isDmdataImportant_noPrev() throws IOException {
        EEW current = parseFixture("forecast_normal.xml");

        assertThat(EEWFilterClassifier.isDmdataImportant(current, null)).isTrue();
    }

    @Test
    void isDmdataImportant_unchanged() throws IOException {
        EEW current = parseFixture("forecast_normal.xml");
        EEW prev = parseFixture("forecast_normal.xml");

        assertThat(EEWFilterClassifier.isDmdataImportant(current, prev)).isFalse();
    }

    @Test
    void isDmdataImportant_lastInfo() throws IOException {
        EEW current = parseFixture("final.xml");
        EEW prev = parseFixture("final.xml");

        assertThat(EEWFilterClassifier.isDmdataImportant(current, prev)).isTrue();
    }

    @Test
    void isDmdataImportant_warningStateChanged() throws IOException {
        EEW current = parseFixture("warning_regions.xml", VXSE43Impl.class);
        EEW prev = parseFixture("forecast_normal.xml");

        assertThat(EEWFilterClassifier.isDmdataImportant(current, prev)).isTrue();
    }

    @Test
    void isDmdataImportant_intensityAppeared() throws IOException {
        EEW current = parseFixture("forecast_normal.xml");
        EEW prev = parseFixture("deep.xml");

        assertThat(EEWFilterClassifier.isDmdataImportant(current, prev)).isTrue();
    }

    @Test
    void isDmdataImportant_maxIntensityChanged() throws IOException {
        EEW current = Codecs.XML_MAPPER.readValue(
                readFixture("forecast_normal.xml").replace("<From>4</From>", "<From>5-</From>"),
                VXSE45Impl.class);
        EEW prev = parseFixture("forecast_normal.xml");

        assertThat(EEWFilterClassifier.isDmdataImportant(current, prev)).isTrue();
    }

    @Test
    void isDmdataImportant_hypocenterChanged() throws IOException {
        EEW current = Codecs.XML_MAPPER.readValue(
                readFixture("forecast_normal.xml").replace("石川県能登地方", "能登半島沖"),
                VXSE45Impl.class);
        EEW prev = parseFixture("forecast_normal.xml");

        assertThat(EEWFilterClassifier.isDmdataImportant(current, prev)).isTrue();
    }

    @Test
    void classifyQuakeInfo() {
        ChannelFilter filter = EEWFilterClassifier.classifyQuakeInfo(SeismicIntensity.SIX_MINUS);

        assertThat(filter.isQuakeInfoPresent()).isTrue();
        assertThat(filter.getQuakeInfo()).isTrue();
        assertThat(filter.isIntensityPresent()).isTrue();
        assertThat(filter.getIntensity()).isEqualTo(SeismicIntensity.SIX_MINUS);
        assertThat(filter.isEewAlertPresent()).isFalse();
        assertThat(filter.isTsunamiPresent()).isFalse();
    }

    @Test
    void classifyTsunami() {
        ChannelFilter filter = EEWFilterClassifier.classifyTsunami();

        assertThat(filter.isTsunamiPresent()).isTrue();
        assertThat(filter.getTsunami()).isTrue();
        assertThat(filter.isQuakeInfoPresent()).isFalse();
        assertThat(filter.isEewAlertPresent()).isFalse();
        assertThat(filter.isIntensityPresent()).isFalse();
    }
}
