package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;

public class DmdataEEW extends DmdataHeader implements Entity {

    public Body body;
    public DmdataEEW prev;

    public static class Body {

        public boolean isLastInfo;
        public boolean isCanceled;
        public boolean isWarning;
        public List<WarningArea> zones;
        public List<WarningArea> prefectures;
        public List<WarningArea> regions;
        public Earthquake earthquake;
        public Intensity intensity;
        public String text;
        public Comments comments;

        public static class WarningArea {

            public WarningAreaKind kind;
            public String code;
            public String name;

            public static class WarningAreaKind {

                public WarningAreaKindLastKind lastKind;
                public String code;
                public String name;

                public static class WarningAreaKindLastKind {

                    public String code;
                    public String name;

                    @Override
                    public String toString() {
                        return "WarningAreaKindLastKind{" +
                                "code='" + code + '\'' +
                                ", name='" + name + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "WarningAreaKind{" +
                            "lastKind=" + lastKind +
                            ", code='" + code + '\'' +
                            ", name='" + name + '\'' +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "WarningArea{" +
                        "kind=" + kind +
                        ", code='" + code + '\'' +
                        ", name='" + name + '\'' +
                        '}';
            }
        }

        public static class Earthquake {

            public String originTime;
            public String arrivalTime;
            public String condition;
            public EarthquakeHypocenter hypocenter;
            public EarthquakeMagnitude magnitude;

            public static class EarthquakeHypocenter {

                public Coordinate coordinate;
                public Depth depth;
                public EarthquakeHypocenterReduce reduce;
                public String landOrSea;
                public EarthquakeHypocenterAccuracy accuracy;
                public String code;
                public String name;

                public static class Coordinate {

                    public LatitudeLongitude latitude;
                    public LatitudeLongitude longitude;
                    public Height height;
                    public String geodeticSystem;
                    public String condition;

                    public static class LatitudeLongitude {

                        public String text;
                        public String value;

                        @Override
                        public String toString() {
                            return "LatitudeLongitude{" +
                                    "text='" + text + '\'' +
                                    ", value='" + value + '\'' +
                                    '}';
                        }
                    }

                    public static class Height {

                        public String type;
                        public String unit;
                        public String value;

                        @Override
                        public String toString() {
                            return "Height{" +
                                    "type='" + type + '\'' +
                                    ", unit='" + unit + '\'' +
                                    ", value='" + value + '\'' +
                                    '}';
                        }
                    }

                    @Override
                    public String toString() {
                        return "Coordinate{" +
                                "latitude=" + latitude +
                                ", longitude=" + longitude +
                                ", height=" + height +
                                ", geodeticSystem='" + geodeticSystem + '\'' +
                                ", condition='" + condition + '\'' +
                                '}';
                    }
                }

                public static class Depth {

                    public String type;
                    public String unit;
                    public String value;
                    public String condition;

                    @Override
                    public String toString() {
                        return "Depth{" +
                                "type='" + type + '\'' +
                                ", unit='" + unit + '\'' +
                                ", value='" + value + '\'' +
                                ", condition='" + condition + '\'' +
                                '}';
                    }
                }

                public static class EarthquakeHypocenterReduce {

                    public String code;
                    public String name;

                    @Override
                    public String toString() {
                        return "EarthquakeHypocenterReduce{" +
                                "code='" + code + '\'' +
                                ", name='" + name + '\'' +
                                '}';
                    }
                }

                public static class EarthquakeHypocenterAccuracy {

                    public List<String> epicenters;
                    public String depth;
                    public String magnitudeCalculation;
                    public String numberOfMagnitudeCalculation;

                    @Override
                    public String toString() {
                        return "EarthquakeHypocenterAccuracy{" +
                                "epicenters=" + epicenters +
                                ", depth='" + depth + '\'' +
                                ", magnitudeCalculation='" + magnitudeCalculation + '\'' +
                                ", numberOfMagnitudeCalculation='" + numberOfMagnitudeCalculation + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "EarthquakeHypocenter{" +
                            "coordinate=" + coordinate +
                            ", depth=" + depth +
                            ", reduce=" + reduce +
                            ", landOrSea='" + landOrSea + '\'' +
                            ", accuracy=" + accuracy +
                            ", code='" + code + '\'' +
                            ", name='" + name + '\'' +
                            '}';
                }
            }

            public static class EarthquakeMagnitude {

                public String type;
                public String unit;
                public String value;
                public String condition;

                @Override
                public String toString() {
                    return "EarthquakeMagnitude{" +
                            "type='" + type + '\'' +
                            ", unit='" + unit + '\'' +
                            ", value='" + value + '\'' +
                            ", condition='" + condition + '\'' +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Earthquake{" +
                        "originTime='" + originTime + '\'' +
                        ", arrivalTime='" + arrivalTime + '\'' +
                        ", condition='" + condition + '\'' +
                        ", hypocenter=" + hypocenter +
                        ", magnitude=" + magnitude +
                        '}';
            }
        }

        public static class Intensity {

            public IntensityForecastMaxInt forecastMaxInt;
            public IntensityForecastLgMaxInt forecastMaxLgInt;
            public IntensityAppendix appendix;
            public List<IntensityRegionReached> regions;

            public static class IntensityForecastMaxInt {

                public String from;
                public String to;

                @Override
                public String toString() {
                    return "IntensityForecastMaxInt{" +
                            "from='" + from + '\'' +
                            ", to='" + to + '\'' +
                            '}';
                }
            }

            public static class IntensityForecastLgMaxInt {

                public String from;
                public String to;

                @Override
                public String toString() {
                    return "IntensityForecastLgMaxInt{" +
                            "from='" + from + '\'' +
                            ", to='" + to + '\'' +
                            '}';
                }
            }

            public static class IntensityAppendix {

                public String maxIntChange;
                public String maxLgIntChange;
                public String maxIntChangeReason;

                @Override
                public String toString() {
                    return "IntensityAppendix{" +
                            "maxIntChange='" + maxIntChange + '\'' +
                            ", maxLgIntChange='" + maxLgIntChange + '\'' +
                            ", maxIntChangeReason='" + maxIntChangeReason + '\'' +
                            '}';
                }
            }

            public static class IntensityRegionReached {

                public String condition;
                public IntensityForecastMaxInt forecastMaxInt;
                public IntensityForecastLgMaxInt forecastMaxLgInt;
                public boolean isPlum;
                public boolean isWarning;
                public IntensityRegionKind kind;
                public String code;
                public String name;

                public static class IntensityRegionKind {

                    public String code;
                    public String name;

                    @Override
                    public String toString() {
                        return "IntensityRegionKind{" +
                                "code='" + code + '\'' +
                                ", name='" + name + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "IntensityRegionReached{" +
                            "condition='" + condition + '\'' +
                            ", forecastMaxInt=" + forecastMaxInt +
                            ", forecastMaxLgInt=" + forecastMaxLgInt +
                            ", isPlum=" + isPlum +
                            ", isWarning=" + isWarning +
                            ", kind=" + kind +
                            ", code='" + code + '\'' +
                            ", name='" + name + '\'' +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Intensity{" +
                        "forecastMaxInt=" + forecastMaxInt +
                        ", forecastMaxLgInt=" + forecastMaxLgInt +
                        ", appendix=" + appendix +
                        ", regions=" + regions +
                        '}';
            }
        }

        public static class Comments {

            public String free;
            public WarningComments warning;

            public static class WarningComments {

                public String text;
                public List<String> codes;

                @Override
                public String toString() {
                    return "WarningComments{" +
                            "text='" + text + '\'' +
                            ", codes=" + codes +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Comments{" +
                        "free='" + free + '\'' +
                        ", warning=" + warning +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Body{" +
                    "isLastInfo=" + isLastInfo +
                    ", isCanceled=" + isCanceled +
                    ", isWarning=" + isWarning +
                    ", zones=" + zones +
                    ", prefectures=" + prefectures +
                    ", regions=" + regions +
                    ", earthquake=" + earthquake +
                    ", intensity=" + intensity +
                    ", text='" + text + '\'' +
                    ", comments=" + comments +
                    '}';
        }
    }

    public boolean isAccurateEnough() {
        return isEpicenterAccurateEnough() && isDepthAccurateEnough() && isMagnitudeAccurateEnough() && !StringUtils.equals(this.body.earthquake.condition, "仮定震源要素");
    }

    public boolean isEpicenterAccurateEnough() {
        for (String acc1 : this.body.earthquake.hypocenter.accuracy.epicenters) {
            if (StringUtils.equalsAny(acc1, "0", "1"))
                return false;
        }
        return true;
    }

    public boolean isDepthAccurateEnough() {
        return !StringUtils.equalsAny(this.body.earthquake.hypocenter.accuracy.depth, "0", "1");
    }

    public boolean isMagnitudeAccurateEnough() {
        return !StringUtils.equalsAny(this.body.earthquake.hypocenter.accuracy.numberOfMagnitudeCalculation, "0", "1");
    }

    @Override
    public MessageCreateSpec createMessage(String lang) {
        if (this.body.isCanceled) {
            return MessageCreateSpec.builder().addEmbed(I18nEmbedCreateSpec.builder(lang)
                            .title("eewbot.eew.eewcancel")
                            .timestamp(FORMAT.parse(this.reportDateTime, Instant::from))
                            .description(this.body.text)
                            .color(Color.YELLOW)
                            .footer("eewbot.eew.projectdmdss", null)
                            .build())
                    .build();
        }
        I18nEmbedCreateSpec.Builder builder = I18nEmbedCreateSpec.builder(lang);
        if (this.body.isWarning) {
            if (this.body.isLastInfo) {
                builder.title("eewbot.eew.eewalert.final");
            } else {
                builder.title("eewbot.eew.eewalert.num", this.serialNo);
            }
            builder.color(Color.RED);
        } else {
            if (this.body.isLastInfo) {
                builder.title("eewbot.eew.eewprediction.final");
            } else {
                builder.title("eewbot.eew.eewprediction.num", this.serialNo);
            }
            builder.color(Color.BLUE);
        }
        builder.timestamp(FORMAT.parse(this.reportDateTime, Instant::from));
        if (!StringUtils.equals(this.body.earthquake.condition, "仮定震源要素")) {
            builder.addField("eewbot.eew.epicenter", this.body.earthquake.hypocenter.name, true);
            if (this.body.earthquake.hypocenter.depth.condition != null) {
                builder.addField("eewbot.eew.depth", this.body.earthquake.hypocenter.depth.condition, true);
            } else {
                builder.addField("eewbot.eew.depth", "eewbot.eew.km", true, this.body.earthquake.hypocenter.depth.value);
            }
            if (this.body.earthquake.magnitude != null) {
                builder.addField("eewbot.eew.magnitude", this.body.earthquake.magnitude.value, true);
            }
        }
        boolean isAccurateEnough = isAccurateEnough();
        if (this.body.intensity != null) {
            builder.addField(isAccurateEnough ? "eewbot.eew.forecastseismicintensity" : "eewbot.eew.seismicintensity",
                    SeismicIntensity.get(this.body.intensity.forecastMaxInt.from).map(SeismicIntensity::getSimple).orElse("eewbot.eew.unknown"),
                    false);
        }
        if (!isAccurateEnough) {
            builder.description("eewbot.eew.inaccurate");
        }
        return MessageCreateSpec.builder().addEmbed(builder.build()).build();
    }

    @Override
    public String toString() {
        return "DmdataEEW{" +
                "body=" + body +
                '}';
    }

}
