package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import org.apache.commons.lang3.StringUtils;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.List;

public class DmdataEEW extends DmdataHeader implements Entity {

    private Body body;
    private DmdataEEW prev;

    public Body getBody() {
        return body;
    }

    public DmdataEEW getPrev() {
        return prev;
    }

    public void setPrev(DmdataEEW prev) {
        this.prev = prev;
    }

    public static class Body {

        private boolean isLastInfo;
        private boolean isCanceled;
        private boolean isWarning;
        private List<WarningArea> zones;
        private List<WarningArea> prefectures;
        private List<WarningArea> regions;
        private Earthquake earthquake;
        private Intensity intensity;
        private String text;
        private Comments comments;

        public boolean isLastInfo() {
            return isLastInfo;
        }

        public boolean isCanceled() {
            return isCanceled;
        }

        public boolean isWarning() {
            return isWarning;
        }

        public List<WarningArea> getZones() {
            return zones;
        }

        public List<WarningArea> getPrefectures() {
            return prefectures;
        }

        public List<WarningArea> getRegions() {
            return regions;
        }

        public Earthquake getEarthquake() {
            return earthquake;
        }

        @Nullable
        public Intensity getIntensity() {
            return intensity;
        }

        public String getText() {
            return text;
        }

        public Comments getComments() {
            return comments;
        }

        public static class WarningArea {

            private WarningAreaKind kind;
            private String code;
            private String name;

            public WarningAreaKind getKind() {
                return kind;
            }

            public String getCode() {
                return code;
            }

            public String getName() {
                return name;
            }

            public static class WarningAreaKind {

                private WarningAreaKindLastKind lastKind;
                private String code;
                private String name;

                public WarningAreaKindLastKind getLastKind() {
                    return lastKind;
                }

                public String getCode() {
                    return code;
                }

                public String getName() {
                    return name;
                }

                public static class WarningAreaKindLastKind {

                    private String code;
                    private String name;

                    public String getCode() {
                        return code;
                    }

                    public String getName() {
                        return name;
                    }

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

            private String originTime;
            private String arrivalTime;
            private String condition;
            private EarthquakeHypocenter hypocenter;
            private EarthquakeMagnitude magnitude;

            public String getOriginTime() {
                return originTime;
            }

            public String getArrivalTime() {
                return arrivalTime;
            }

            public String getCondition() {
                return condition;
            }

            public EarthquakeHypocenter getHypocenter() {
                return hypocenter;
            }

            public EarthquakeMagnitude getMagnitude() {
                return magnitude;
            }

            public static class EarthquakeHypocenter {

                private Coordinate coordinate;
                private Depth depth;
                private EarthquakeHypocenterReduce reduce;
                private String landOrSea;
                private EarthquakeHypocenterAccuracy accuracy;
                private String code;
                private String name;

                public Coordinate getCoordinate() {
                    return coordinate;
                }

                public Depth getDepth() {
                    return depth;
                }

                public EarthquakeHypocenterReduce getReduce() {
                    return reduce;
                }

                public String getLandOrSea() {
                    return landOrSea;
                }

                public EarthquakeHypocenterAccuracy getAccuracy() {
                    return accuracy;
                }

                public String getCode() {
                    return code;
                }

                public String getName() {
                    return name;
                }

                public static class Coordinate {

                    private LatitudeLongitude latitude;
                    private LatitudeLongitude longitude;
                    private Height height;
                    private String geodeticSystem;
                    private String condition;

                    public LatitudeLongitude getLatitude() {
                        return latitude;
                    }

                    public LatitudeLongitude getLongitude() {
                        return longitude;
                    }

                    public Height getHeight() {
                        return height;
                    }

                    public String getGeodeticSystem() {
                        return geodeticSystem;
                    }

                    public String getCondition() {
                        return condition;
                    }

                    public static class LatitudeLongitude {

                        private String text;
                        private String value;

                        public String getText() {
                            return text;
                        }

                        public String getValue() {
                            return value;
                        }

                        @Override
                        public String toString() {
                            return "LatitudeLongitude{" +
                                    "text='" + text + '\'' +
                                    ", value='" + value + '\'' +
                                    '}';
                        }
                    }

                    public static class Height {

                        private String type;
                        private String unit;
                        private String value;

                        public String getType() {
                            return type;
                        }

                        public String getUnit() {
                            return unit;
                        }

                        public String getValue() {
                            return value;
                        }

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

                    private String type;
                    private String unit;
                    private String value;
                    private String condition;

                    public String getType() {
                        return type;
                    }

                    public String getUnit() {
                        return unit;
                    }

                    public String getValue() {
                        return value;
                    }

                    public String getCondition() {
                        return condition;
                    }

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

                    private String code;
                    private String name;

                    public String getCode() {
                        return code;
                    }

                    public String getName() {
                        return name;
                    }

                    @Override
                    public String toString() {
                        return "EarthquakeHypocenterReduce{" +
                                "code='" + code + '\'' +
                                ", name='" + name + '\'' +
                                '}';
                    }
                }

                public static class EarthquakeHypocenterAccuracy {

                    private List<String> epicenters;
                    private String depth;
                    private String magnitudeCalculation;
                    private String numberOfMagnitudeCalculation;

                    public List<String> getEpicenters() {
                        return epicenters;
                    }

                    public String getDepth() {
                        return depth;
                    }

                    public String getMagnitudeCalculation() {
                        return magnitudeCalculation;
                    }

                    public String getNumberOfMagnitudeCalculation() {
                        return numberOfMagnitudeCalculation;
                    }

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

                private String type;
                private String unit;
                private String value;
                private String condition;

                public String getType() {
                    return type;
                }

                public String getUnit() {
                    return unit;
                }

                public String getValue() {
                    return value;
                }

                public String getCondition() {
                    return condition;
                }

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

            private IntensityForecastMaxInt forecastMaxInt;
            private IntensityForecastLgMaxInt forecastMaxLgInt;
            private IntensityAppendix appendix;
            private List<IntensityRegionReached> regions;

            public IntensityForecastMaxInt getForecastMaxInt() {
                return forecastMaxInt;
            }

            public IntensityForecastLgMaxInt getForecastMaxLgInt() {
                return forecastMaxLgInt;
            }

            public IntensityAppendix getAppendix() {
                return appendix;
            }

            public List<IntensityRegionReached> getRegions() {
                return regions;
            }

            public static class IntensityForecastMaxInt {

                private String from;
                private String to;

                public String getFrom() {
                    return from;
                }

                public String getTo() {
                    return to;
                }

                @Override
                public String toString() {
                    return "IntensityForecastMaxInt{" +
                            "from='" + from + '\'' +
                            ", to='" + to + '\'' +
                            '}';
                }
            }

            public static class IntensityForecastLgMaxInt {

                private String from;
                private String to;

                public String getFrom() {
                    return from;
                }

                public String getTo() {
                    return to;
                }

                @Override
                public String toString() {
                    return "IntensityForecastLgMaxInt{" +
                            "from='" + from + '\'' +
                            ", to='" + to + '\'' +
                            '}';
                }
            }

            public static class IntensityAppendix {

                private String maxIntChange;
                private String maxLgIntChange;
                private String maxIntChangeReason;

                public String getMaxIntChange() {
                    return maxIntChange;
                }

                public String getMaxLgIntChange() {
                    return maxLgIntChange;
                }

                public String getMaxIntChangeReason() {
                    return maxIntChangeReason;
                }

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

                private String condition;
                private IntensityForecastMaxInt forecastMaxInt;
                private IntensityForecastLgMaxInt forecastMaxLgInt;
                private boolean isPlum;
                private boolean isWarning;
                private IntensityRegionKind kind;
                private String code;
                private String name;

                public String getCondition() {
                    return condition;
                }

                public IntensityForecastMaxInt getForecastMaxInt() {
                    return forecastMaxInt;
                }

                public IntensityForecastLgMaxInt getForecastMaxLgInt() {
                    return forecastMaxLgInt;
                }

                public boolean isPlum() {
                    return isPlum;
                }

                public boolean isWarning() {
                    return isWarning;
                }

                public IntensityRegionKind getKind() {
                    return kind;
                }

                public String getCode() {
                    return code;
                }

                public String getName() {
                    return name;
                }

                public static class IntensityRegionKind {

                    private String code;
                    private String name;

                    public String getCode() {
                        return code;
                    }

                    public String getName() {
                        return name;
                    }

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

            private String free;
            private WarningComments warning;

            public String getFree() {
                return free;
            }

            public WarningComments getWarning() {
                return warning;
            }

            public static class WarningComments {

                private String text;
                private List<String> codes;

                public String getText() {
                    return text;
                }

                public List<String> getCodes() {
                    return codes;
                }

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
        return isEpicenterAccurateEnough() && isDepthAccurateEnough() && isMagnitudeAccurateEnough() && !StringUtils.equals(this.getBody().getEarthquake().getCondition(), "仮定震源要素");
    }

    public boolean isEpicenterAccurateEnough() {
        for (String acc1 : this.getBody().getEarthquake().getHypocenter().getAccuracy().getEpicenters()) {
            if (StringUtils.equalsAny(acc1, "0", "1"))
                return false;
        }
        return true;
    }

    public boolean isDepthAccurateEnough() {
        return !StringUtils.equalsAny(this.getBody().getEarthquake().getHypocenter().getAccuracy().getDepth(), "0", "1");
    }

    public boolean isMagnitudeAccurateEnough() {
        return !(this.getBody().getEarthquake().getHypocenter().getAccuracy().getMagnitudeCalculation().equals("0") ||
                this.getBody().getEarthquake().getHypocenter().getAccuracy().getNumberOfMagnitudeCalculation().equals("1"));
    }

    @Override
    public MessageCreateSpec createMessage(String lang) {
        if (this.getBody().isCanceled()) {
            return MessageCreateSpec.builder().addEmbed(I18nEmbedCreateSpec.builder(lang)
                            .title("eewbot.eew.eewcancel")
                            .timestamp(FORMAT.parse(this.getReportDateTime(), Instant::from))
                            .description(this.getBody().getText())
                            .color(Color.YELLOW)
                            .footer(String.join(" ", this.getPublishingOffice()), null)
                            .build())
                    .build();
        }

        I18nEmbedCreateSpec.Builder builder = I18nEmbedCreateSpec.builder(lang);
        if (this.getBody().isWarning()) {
            if (this.getBody().isLastInfo()) {
                builder.title("eewbot.eew.eewalert.final");
            } else {
                builder.title("eewbot.eew.eewalert.num", this.getSerialNo());
            }
            builder.color(Color.RED);
        } else {
            if (this.getBody().isLastInfo()) {
                builder.title("eewbot.eew.eewprediction.final");
            } else {
                builder.title("eewbot.eew.eewprediction.num", this.getSerialNo());
            }
            builder.color(Color.BLUE);
        }
        builder.timestamp(FORMAT.parse(this.getReportDateTime(), Instant::from));
        if (!StringUtils.equals(this.getBody().getEarthquake().getCondition(), "仮定震源要素")) {
            builder.addField("eewbot.eew.epicenter", this.getBody().getEarthquake().getHypocenter().getName(), true);
            if (this.getBody().getEarthquake().getHypocenter().getDepth().getCondition() != null) {
                builder.addField("eewbot.eew.depth", this.getBody().getEarthquake().getHypocenter().getDepth().getCondition(), true);
            } else {
                builder.addField("eewbot.eew.depth", "eewbot.eew.km", true, this.getBody().getEarthquake().getHypocenter().getDepth().getValue());
            }
            if (this.getBody().getEarthquake().getMagnitude() != null) {
                builder.addField("eewbot.eew.magnitude", this.getBody().getEarthquake().getMagnitude().getValue(), true);
            }
        }
        boolean isAccurateEnough = isAccurateEnough();
        if (this.getBody().getIntensity() != null) {
            builder.addField(isAccurateEnough ? "eewbot.eew.forecastseismicintensity" : "eewbot.eew.seismicintensity",
                    SeismicIntensity.get(this.getBody().getIntensity().getForecastMaxInt().getFrom()).map(SeismicIntensity::getSimple).orElse("eewbot.eew.unknown"),
                    false);
        }
        if (!isAccurateEnough) {
            builder.description("eewbot.eew.inaccurate");
        }
        builder.footer(String.join(" ", this.getPublishingOffice()), null);
        return MessageCreateSpec.builder().addEmbed(builder.build()).build();
    }

    @Override
    public String toString() {
        return "DmdataEEW{" +
                "body=" + body +
                '}';
    }

}
