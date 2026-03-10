package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class Tsunami {

    @JacksonXmlProperty(localName = "Release")
    private @Nullable String release;

    @JacksonXmlProperty(localName = "Observation")
    private @Nullable TsunamiDetail observation;

    @JacksonXmlProperty(localName = "Estimation")
    private @Nullable TsunamiDetail estimation;

    @JacksonXmlProperty(localName = "Forecast")
    private @Nullable TsunamiDetail forecast;

    @Nullable
    public String getRelease() {
        return this.release;
    }

    @Nullable
    public TsunamiDetail getObservation() {
        return this.observation;
    }

    @Nullable
    public TsunamiDetail getEstimation() {
        return this.estimation;
    }

    @Nullable
    public TsunamiDetail getForecast() {
        return this.forecast;
    }

    @Override
    public String toString() {
        return "Tsunami{" +
                "release='" + this.release + '\'' +
                ", observation=" + this.observation +
                ", estimation=" + this.estimation +
                ", forecast=" + this.forecast +
                '}';
    }
}
