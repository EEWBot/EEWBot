package net.teamfruit.eewbot.entity.jma.telegram.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import reactor.util.annotation.Nullable;

import java.util.Optional;

@SuppressWarnings("unused")
public class Magnitude {

    @JacksonXmlText
    private float value;

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String condition;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String description;

    public float getRawValue() {
        return this.value;
    }

    public String getType() {
        return this.type;
    }

    public Optional<String> getCondition() {
        return Optional.ofNullable(this.condition);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(this.description);
    }

    public String getMagnitude() {
        if (Float.isNaN(this.value)) {
            return this.description;
        }
        return String.valueOf(this.value);
    }

    @Override
    public String toString() {
        return "Magnitude{" +
                "value='" + this.value + '\'' +
                ", type='" + this.type + '\'' +
                ", condition='" + this.condition + '\'' +
                ", description='" + this.description + '\'' +
                '}';
    }
}
