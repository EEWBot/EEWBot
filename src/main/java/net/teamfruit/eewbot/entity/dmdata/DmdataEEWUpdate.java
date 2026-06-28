package net.teamfruit.eewbot.entity.dmdata;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.jetbrains.annotations.Nullable;

public record DmdataEEWUpdate(DmdataEEW current, @Nullable DmdataEEW prev, SeismicIntensity maxIntensityBefore) {

    public SeismicIntensity maxIntensityEEW() {
        DmdataEEW.Body.Intensity intensity = this.current.getBody().getIntensity();
        if (intensity == null)
            return this.maxIntensityBefore;
        SeismicIntensity bodyIntensity = SeismicIntensity.get(intensity.getForecastMaxInt().getFrom());
        return bodyIntensity.compareTo(this.maxIntensityBefore) > 0 ? bodyIntensity : this.maxIntensityBefore;
    }
}
