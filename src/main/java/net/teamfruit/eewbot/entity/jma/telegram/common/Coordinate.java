package net.teamfruit.eewbot.entity.jma.telegram.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import reactor.util.annotation.Nullable;

import java.util.Optional;

public class Coordinate {

    @JacksonXmlText
    private String value;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String type;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String datum;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String condition;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String description;

    public String getValue() {
        return this.value;
    }

    public Optional<String> getType() {
        return Optional.ofNullable(this.type);
    }

    public Optional<String> getDatum() {
        return Optional.ofNullable(this.datum);
    }

    public Optional<String> getCondition() {
        return Optional.ofNullable(this.condition);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(this.description);
    }

    @Override
    public String toString() {
        return "Coordinate{" +
                "value='" + this.value + '\'' +
                ", type='" + this.type + '\'' +
                ", datum='" + this.datum + '\'' +
                ", condition='" + this.condition + '\'' +
                ", description='" + this.description + '\'' +
                '}';
    }
}
