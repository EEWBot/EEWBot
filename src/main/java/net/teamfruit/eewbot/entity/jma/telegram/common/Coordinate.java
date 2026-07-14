package net.teamfruit.eewbot.entity.jma.telegram.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import org.jetbrains.annotations.Nullable;

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
    private @Nullable String depthValue;
    private @Nullable String depthCondition;

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

    @Nullable
    public String getDepthValue() {
        if (this.value == null) {
            return null;
        }
        parseCoordIfNotParsed();
        return this.depthValue;
    }

    @Nullable
    public String getDepthCondition() {
        if (this.value == null) {
            return null;
        }
        parseCoordIfNotParsed();
        return this.depthCondition;
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
                parseDepth(matcher.group(3));
            }
        }
        this.isParsed = true;
    }

    // depth("10km"形式)に加え、EEW側でdmdata JSONのdepth.value/depth.conditionを再現するため数値(km)と状態("ごく浅い"等)にも分離して保持する
    private void parseDepth(@Nullable String depthStr) {
        if (depthStr == null) {
            this.depth = "不明";
            this.depthCondition = "不明";
            return;
        }
        int depthMeters = Integer.parseInt(depthStr);
        // 度分形式のdepthは特殊表記("ごく浅い"等)にせず常に数値表記
        boolean degreeMinute = "震源位置（度分）".equals(getType());
        if (depthMeters >= 0) {
            if (!degreeMinute) {
                this.depth = "ごく浅い";
            }
            this.depthCondition = "ごく浅い";
        } else if (depthMeters <= -700000) {
            if (!degreeMinute) {
                this.depth = "700km以上";
            }
            // dmdata JSONのdepth.conditionは全角表記の"７００ｋｍ以上"
            this.depthCondition = "７００ｋｍ以上";
        } else {
            this.depthValue = String.valueOf(-depthMeters / 1000);
        }
        if (this.depth == null) {
            this.depth = -depthMeters / 1000 + "km";
        }
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
