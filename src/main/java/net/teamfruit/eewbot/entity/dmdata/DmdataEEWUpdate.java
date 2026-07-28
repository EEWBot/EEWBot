package net.teamfruit.eewbot.entity.dmdata;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import org.jetbrains.annotations.Nullable;

public record DmdataEEWUpdate(EEW current, @Nullable EEW prev, SeismicIntensity maxIntensityBefore) {

    public SeismicIntensity maxIntensityEEW() {
        EEW.ForecastMaxInt maxInt = this.current.getForecastMaxInt();
        if (maxInt == null)
            return this.maxIntensityBefore;
        SeismicIntensity bodyIntensity = SeismicIntensity.get(maxInt.from());
        return bodyIntensity.compareTo(this.maxIntensityBefore) > 0 ? bodyIntensity : this.maxIntensityBefore;
    }
}
