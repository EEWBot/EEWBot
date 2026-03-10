package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

@SuppressWarnings("unused")
public class FirstHeight {

    @JacksonXmlProperty(localName = "ArrivalTimeFrom")
    private @Nullable Instant arrivalTimeFrom;

    @JacksonXmlProperty(localName = "ArrivalTimeTo")
    private @Nullable Instant arrivalTimeTo;

    @JacksonXmlProperty(localName = "ArrivalTime")
    private @Nullable Instant arrivalTime;

    @JacksonXmlProperty(localName = "Condition")
    private @Nullable String condition;

    @JacksonXmlProperty(localName = "Initial")
    private @Nullable String initial;

    @JacksonXmlProperty(localName = "TsunamiHeight")
    private @Nullable TsunamiHeight tsunamiHeight;

    @JacksonXmlProperty(localName = "Revise")
    private @Nullable String revise;

    @JacksonXmlProperty(localName = "Period")
    private @Nullable Float period;

    @Nullable
    public Instant getArrivalTimeFrom() {
        return this.arrivalTimeFrom;
    }

    @Nullable
    public Instant getArrivalTimeTo() {
        return this.arrivalTimeTo;
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
    public String getInitial() {
        return this.initial;
    }

    @Nullable
    public TsunamiHeight getTsunamiHeight() {
        return this.tsunamiHeight;
    }

    @Nullable
    public String getRevise() {
        return this.revise;
    }

    @Nullable
    public Float getPeriod() {
        return this.period;
    }

    @Override
    public String toString() {
        return "FirstHeight{" +
                "arrivalTimeFrom=" + this.arrivalTimeFrom +
                ", arrivalTimeTo=" + this.arrivalTimeTo +
                ", arrivalTime=" + this.arrivalTime +
                ", condition='" + this.condition + '\'' +
                ", initial='" + this.initial + '\'' +
                ", tsunamiHeight=" + this.tsunamiHeight +
                ", revise='" + this.revise + '\'' +
                ", period=" + this.period +
                '}';
    }
}
