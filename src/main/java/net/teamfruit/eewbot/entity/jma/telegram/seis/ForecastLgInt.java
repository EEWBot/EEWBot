package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

@SuppressWarnings("unused")
public class ForecastLgInt {

    @JacksonXmlProperty(localName = "From")
    private @Nullable String from;

    @JacksonXmlProperty(localName = "To")
    private @Nullable String to;

    @Nullable
    public String getFrom() {
        return this.from;
    }

    @Nullable
    public String getTo() {
        return this.to;
    }

    @Override
    public String toString() {
        return "ForecastLgInt{" +
                "from=" + this.from +
                ", to=" + this.to +
                '}';
    }
}