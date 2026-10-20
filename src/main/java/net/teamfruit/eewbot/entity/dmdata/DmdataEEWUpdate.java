package net.teamfruit.eewbot.entity.dmdata;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Nullable;

public record DmdataEEWUpdate(EEW current, @Nullable EEW prev, SeismicIntensity maxIntensityBefore,
                              DeliveryHistory deliveryHistory) {

    public boolean isDeliveryTarget() {
        return this.current.isCancelReport() || !(this.current.hasEarthquake() &&
                Strings.CS.equals(this.current.getCondition(), "仮定震源要素") &&
                this.current.getForecastRegions() == null);
    }

    /** Null means that this classification has never been offered for delivery. */
    public record DeliveryHistory(@Nullable SeismicIntensity warningMaxIntensity,
                                  @Nullable SeismicIntensity forecastMaxIntensity) {
        public static final DeliveryHistory EMPTY = new DeliveryHistory(null, null);

        public boolean isEmpty() {
            return this.warningMaxIntensity == null && this.forecastMaxIntensity == null;
        }

        public DeliveryHistory include(boolean warning, SeismicIntensity intensity) {
            if (warning)
                return new DeliveryHistory(max(this.warningMaxIntensity, intensity), this.forecastMaxIntensity);
            return new DeliveryHistory(this.warningMaxIntensity, max(this.forecastMaxIntensity, intensity));
        }

        private static SeismicIntensity max(@Nullable SeismicIntensity previous, SeismicIntensity current) {
            return previous == null || current.compareTo(previous) > 0 ? current : previous;
        }
    }

    public SeismicIntensity maxIntensityEEW() {
        EEW.ForecastMaxInt maxInt = this.current.getForecastMaxInt();
        if (maxInt == null)
            return this.maxIntensityBefore;
        SeismicIntensity bodyIntensity = SeismicIntensity.get(maxInt.from());
        return bodyIntensity.compareTo(this.maxIntensityBefore) > 0 ? bodyIntensity : this.maxIntensityBefore;
    }
}
