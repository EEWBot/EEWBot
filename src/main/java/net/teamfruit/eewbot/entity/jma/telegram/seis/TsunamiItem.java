package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@SuppressWarnings("unused")
public class TsunamiItem {

    @JacksonXmlProperty(localName = "Area")
    private ForecastArea area;

    @JacksonXmlProperty(localName = "Category")
    private @Nullable Category category;

    @JacksonXmlProperty(localName = "FirstHeight")
    private @Nullable FirstHeight firstHeight;

    @JacksonXmlProperty(localName = "MaxHeight")
    private @Nullable MaxHeight maxHeight;

    @JacksonXmlProperty(localName = "Duration")
    private @Nullable Duration duration;

    @JacksonXmlProperty(localName = "Station")
    @JacksonXmlElementWrapper(useWrapping = false)
    private @Nullable List<TsunamiStation> stations;

    public ForecastArea getArea() {
        return this.area;
    }

    @Nullable
    public Category getCategory() {
        return this.category;
    }

    @Nullable
    public FirstHeight getFirstHeight() {
        return this.firstHeight;
    }

    @Nullable
    public MaxHeight getMaxHeight() {
        return this.maxHeight;
    }

    @Nullable
    public Duration getDuration() {
        return this.duration;
    }

    @Nullable
    public List<TsunamiStation> getStations() {
        return this.stations;
    }

    @Override
    public String toString() {
        return "TsunamiItem{" +
                "area=" + this.area +
                ", category=" + this.category +
                ", firstHeight=" + this.firstHeight +
                ", maxHeight=" + this.maxHeight +
                ", duration=" + this.duration +
                ", stations=" + this.stations +
                '}';
    }
}
