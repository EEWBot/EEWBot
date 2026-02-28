package net.teamfruit.eewbot.entity.jma.telegram.seis;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import reactor.util.annotation.Nullable;

@SuppressWarnings("unused")
public class TsunamiHeight {

    @JacksonXmlText
    private float value;

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(isAttribute = true)
    private String unit;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String condition;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String description;

    public float getValue() {
        return this.value;
    }

    public String getType() {
        return this.type;
    }

    public String getUnit() {
        return this.unit;
    }

    @Nullable
    public String getCondition() {
        return this.condition;
    }

    @Nullable
    public String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return "TsunamiHeight{" +
                "value=" + this.value +
                ", type='" + this.type + '\'' +
                ", unit='" + this.unit + '\'' +
                ", condition='" + this.condition + '\'' +
                ", description='" + this.description + '\'' +
                '}';
    }
}
