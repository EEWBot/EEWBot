package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

import java.time.Instant;

@SuppressWarnings("unused")
public class MaxHeight {

    @JacksonXmlProperty(localName = "DateTime")
    private @Nullable Instant dateTime;

    @JacksonXmlProperty(localName = "Condition")
    private @Nullable String condition;

    @JacksonXmlProperty(localName = "TsunamiHeightFrom")
    private @Nullable TsunamiHeight tsunamiHeightFrom;

    @JacksonXmlProperty(localName = "TsunamiHeightTo")
    private @Nullable TsunamiHeight tsunamiHeightTo;

    @JacksonXmlProperty(localName = "TsunamiHeight")
    private @Nullable TsunamiHeight tsunamiHeight;

    @JacksonXmlProperty(localName = "Revise")
    private @Nullable String revise;

    @JacksonXmlProperty(localName = "Period")
    private @Nullable Float period;

    @Nullable
    public Instant getDateTime() {
        return this.dateTime;
    }

    @Nullable
    public String getCondition() {
        return this.condition;
    }

    @Nullable
    public TsunamiHeight getTsunamiHeightFrom() {
        return this.tsunamiHeightFrom;
    }

    @Nullable
    public TsunamiHeight getTsunamiHeightTo() {
        return this.tsunamiHeightTo;
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
        return "MaxHeight{" +
                "dateTime=" + this.dateTime +
                ", condition='" + this.condition + '\'' +
                ", tsunamiHeightFrom=" + this.tsunamiHeightFrom +
                ", tsunamiHeightTo=" + this.tsunamiHeightTo +
                ", tsunamiHeight=" + this.tsunamiHeight +
                ", revise='" + this.revise + '\'' +
                ", period=" + this.period +
                '}';
    }
}
