package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

import java.time.Instant;

@SuppressWarnings("unused")
public class Earthquake {

    @JacksonXmlProperty(localName = "OriginTime")
    private @Nullable Instant originTime;

    @JacksonXmlProperty(localName = "ArrivalTime")
    private Instant arrivalTime;

    @JacksonXmlProperty(localName = "Hypocenter")
    private @Nullable Hypocenter hypocenter;

    @JacksonXmlProperty(localName = "Magnitude")
    private @Nullable Magnitude magnitude;

    @Nullable
    public Instant getOriginTime() {
        return this.originTime;
    }

    public Instant getArrivalTime() {
        return this.arrivalTime;
    }

    @Nullable
    public Hypocenter getHypocenter() {
        return this.hypocenter;
    }

    @Nullable
    public Magnitude getMagnitude() {
        return this.magnitude;
    }

    @Override
    public String toString() {
        return "Earthquake{" +
                "originTime=" + this.originTime +
                ", arrivalTime=" + this.arrivalTime +
                ", hypocenter=" + this.hypocenter +
                ", magnitude=" + this.magnitude +
                '}';
    }
}
