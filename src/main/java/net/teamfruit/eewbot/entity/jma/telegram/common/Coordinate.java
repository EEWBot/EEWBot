package net.teamfruit.eewbot.entity.jma.telegram.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import reactor.util.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class Coordinate {

    private static final Pattern ISO6709_PATTERN = Pattern.compile("^\\s*([+-][\\d.]*)([+-][\\d.]*)([+-]\\d*)?/\\s*$");

    @JacksonXmlText
    private @Nullable String value;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String type;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String datum;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String condition;

    @JacksonXmlProperty(isAttribute = true)
    private @Nullable String description;

    private boolean isParsed = false;
    private float lat;
    private float lon;
    private @Nullable String depth;

    @Nullable
    public String getRawValue() {
        return this.value;
    }

    @Nullable
    public String getType() {
        return this.type;
    }

    @Nullable
    public String getDatum() {
        return this.datum;
    }

    @Nullable
    public String getCondition() {
        return this.condition;
    }

    @Nullable
    public String getDescription() {
        return this.description;
    }

    @Nullable
    public Float getLat() {
        if (this.value == null) {
            return null;
        }
        parseCoordIfNotParsed();
        return this.lat;
    }

    @Nullable
    public Float getLon() {
        if (this.value == null) {
            return null;
        }
        parseCoordIfNotParsed();
        return this.lon;
    }

    @Nullable
    public String getDepth() {
        if (this.value == null) {
            return null;
        }
        parseCoordIfNotParsed();
        return this.depth;
    }

    private void parseCoordIfNotParsed() {
        if (this.isParsed) {
            return;
        }
        if (this.value != null) {
            Matcher matcher = ISO6709_PATTERN.matcher(this.value);
            if (matcher.matches()) {
                this.lat = Float.parseFloat(matcher.group(1));
                this.lon = Float.parseFloat(matcher.group(2));
                this.depth = parseDepth(matcher.group(3));
            }
        }
        this.isParsed = true;
    }

    private String parseDepth(String depthStr) {
        if (depthStr == null) {
            return "不明";
        }
        int depth = Integer.parseInt(depthStr);
        if (depth >= 0) {
            return "ごく浅い";
        }
        if (depth <= -700000) {
            return "700km以上";
        }
        return -depth / 1000 + "km";
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
