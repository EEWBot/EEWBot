package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EEWFilterClassifierTest {

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
