package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.LgIntensity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.List;

@SuppressWarnings("unused")
public class IntensityCity {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Code")
    private String code;

    @JacksonXmlProperty(localName = "Category")
    private @Nullable Category category;

    @JacksonXmlProperty(localName = "MaxInt")
    private @Nullable SeismicIntensity maxInt;

    @JacksonXmlProperty(localName = "MaxLgInt")
    private @Nullable LgIntensity maxLgInt;

    @JacksonXmlProperty(localName = "ForecastInt")
    private @Nullable ForecastInt forecastInt;

    @JacksonXmlProperty(localName = "ForecastLgInt")
    private @Nullable ForecastLgInt forecastLgInt;

    @JacksonXmlProperty(localName = "ArrivalTime")
    private @Nullable Instant arrivalTime;

    @JacksonXmlProperty(localName = "Condition")
    private @Nullable String condition;

    @JacksonXmlProperty(localName = "Revise")
    private @Nullable String revise;

    @JacksonXmlProperty(localName = "IntensityStation")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<IntensityStation> stations;

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }

    @Nullable
    public Category getCategory() {
        return this.category;
    }

    @Nullable
    public SeismicIntensity getMaxInt() {
        return this.maxInt;
    }

    @Nullable
    public LgIntensity getMaxLgInt() {
        return this.maxLgInt;
    }

    @Nullable
    public ForecastInt getForecastInt() {
        return this.forecastInt;
    }

    @Nullable
    public ForecastLgInt getForecastLgInt() {
        return this.forecastLgInt;
    }

    @Nullable
    public Instant getArrivalTime() {
        return this.arrivalTime;
    }

    @Nullable
    public String getCondition() {
        return this.condition;
    }

    @Nullable
    public String getRevise() {
        return this.revise;
    }

    public List<IntensityStation> getStations() {
        return this.stations;
    }

    @Override
    public String toString() {
        return "IntensityCity{" +
                "name='" + this.name + '\'' +
                ", code='" + this.code + '\'' +
                ", category=" + this.category +
                ", maxInt=" + this.maxInt +
                ", maxLgInt=" + this.maxLgInt +
                ", forecastInt=" + this.forecastInt +
                ", forecastLgInt=" + this.forecastLgInt +
                ", arrivalTime=" + this.arrivalTime +
                ", condition='" + this.condition + '\'' +
                ", revise='" + this.revise + '\'' +
                ", stations=" + this.stations +
                '}';
    }
}
