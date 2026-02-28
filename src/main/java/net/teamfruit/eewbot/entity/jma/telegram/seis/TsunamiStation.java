package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

import java.time.Instant;

@SuppressWarnings("unused")
public class TsunamiStation {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Code")
    private String code;

    @JacksonXmlProperty(localName = "Sensor")
    private @Nullable String sensor;

    @JacksonXmlProperty(localName = "HighTideDateTime")
    private @Nullable Instant highTideDateTime;

    @JacksonXmlProperty(localName = "FirstHeight")
    private FirstHeight firstHeight;

    @JacksonXmlProperty(localName = "MaxHeight")
    private @Nullable MaxHeight maxHeight;

    @JacksonXmlProperty(localName = "CurrentHeight")
    private @Nullable CurrentHeight currentHeight;

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }

    @Nullable
    public String getSensor() {
        return this.sensor;
    }

    @Nullable
    public Instant getHighTideDateTime() {
        return this.highTideDateTime;
    }

    public FirstHeight getFirstHeight() {
        return this.firstHeight;
    }

    @Nullable
    public MaxHeight getMaxHeight() {
        return this.maxHeight;
    }

    @Nullable
    public CurrentHeight getCurrentHeight() {
        return this.currentHeight;
    }

    @Override
    public String toString() {
        return "TsunamiStation{" +
                "name='" + this.name + '\'' +
                ", code='" + this.code + '\'' +
                ", sensor='" + this.sensor + '\'' +
                ", highTideDateTime=" + this.highTideDateTime +
                ", firstHeight=" + this.firstHeight +
                ", maxHeight=" + this.maxHeight +
                ", currentHeight=" + this.currentHeight +
                '}';
    }
}
