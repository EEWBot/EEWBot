package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public class ForecastArea {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Code")
    private String code;

    @JacksonXmlProperty(localName = "City")
    @JacksonXmlElementWrapper(useWrapping = false)
    private @Nullable List<ForecastCity> cities;

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }

    @Nullable
    public List<ForecastCity> getCities() {
        return this.cities;
    }

    @Override
    public String toString() {
        return "ForecastArea{" +
                "name='" + this.name + '\'' +
                ", code='" + this.code + '\'' +
                ", cities=" + this.cities +
                '}';
    }
}
