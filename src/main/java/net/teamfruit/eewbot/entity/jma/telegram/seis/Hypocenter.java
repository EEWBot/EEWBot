package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import reactor.util.annotation.Nullable;

@SuppressWarnings("unused")
public class Hypocenter {

    @JacksonXmlProperty(localName = "Area")
    private HypoArea hypoArea;

    @JacksonXmlProperty(localName = "Source")
    private @Nullable String source;

    @JacksonXmlProperty(localName = "Accuracy")
    private @Nullable Accuracy accuracy;

    public HypoArea getArea() {
        return this.hypoArea;
    }

    @Nullable
    public String getSource() {
        return this.source;
    }

    @Nullable
    public Accuracy getAccuracy() {
        return this.accuracy;
    }

    @Override
    public String toString() {
        return "Hypocenter{" +
                "hypoArea=" + this.hypoArea +
                ", source='" + this.source + '\'' +
                ", accuracy=" + this.accuracy +
                '}';
    }
}
