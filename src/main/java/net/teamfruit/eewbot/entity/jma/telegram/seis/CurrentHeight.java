package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

import java.time.Instant;

@SuppressWarnings("unused")
public class CurrentHeight {

    @JacksonXmlProperty(localName = "StartTime")
    private @Nullable Instant startTime;

    @JacksonXmlProperty(localName = "EndTime")
    private @Nullable Instant endTime;

    @JacksonXmlProperty(localName = "Condition")
    private @Nullable String condition;

    @JacksonXmlProperty(localName = "TsunamiHeight")
    private @Nullable TsunamiHeight tsunamiHeight;

    @Nullable
    public Instant getStartTime() {
        return this.startTime;
    }

    @Nullable
    public Instant getEndTime() {
        return this.endTime;
    }

    @Nullable
    public String getCondition() {
        return this.condition;
    }

    @Nullable
    public TsunamiHeight getTsunamiHeight() {
        return this.tsunamiHeight;
    }

    @Override
    public String toString() {
        return "CurrentHeight{" +
                "startTime=" + this.startTime +
                ", endTime=" + this.endTime +
                ", condition='" + this.condition + '\'' +
                ", tsunamiHeight=" + this.tsunamiHeight +
                '}';
    }
}
