package net.teamfruit.eewbot.entity.dmdata;

import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.i18n.I18nDiscordEmbed;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import org.apache.commons.lang3.StringUtils;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DmdataEEW extends DmdataHeader implements Entity {

    private Body body;
    private DmdataEEW prev;
    private SeismicIntensity maxIntensityBefore = SeismicIntensity.UNKNOWN;
    private boolean concurrent;
    private int concurrentIndex;

    public Body getBody() {
        return this.body;
    }

    @Nullable
    public DmdataEEW getPrev() {
        return this.prev;
    }

    public void setPrev(DmdataEEW prev) {
        this.prev = prev;
        SeismicIntensity prevIntensity = prev.getBody().getIntensity() != null ?
                SeismicIntensity.get(prev.getBody().getIntensity().getForecastMaxInt().getFrom()) : SeismicIntensity.UNKNOWN;
        if (this.maxIntensityBefore.compareTo(prevIntensity) < 0)
            this.maxIntensityBefore = prevIntensity;
    }

    public SeismicIntensity getMaxIntensityEEW() {
        if (getBody().getIntensity() == null)
            return this.maxIntensityBefore;
        SeismicIntensity intensity = SeismicIntensity.get(getBody().getIntensity().getForecastMaxInt().getFrom());
        if (intensity.compareTo(this.maxIntensityBefore) > 0)
            return intensity;
        return this.maxIntensityBefore;
    }

    public boolean isConcurrent() {
        return this.concurrent;
    }

    public void setConcurrent(boolean concurrent) {
        this.concurrent = concurrent;
    }

    public int getConcurrentIndex() {
        return this.concurrentIndex;
    }

    public void setConcurrentIndex(int concurrentIndex) {
        this.concurrentIndex = concurrentIndex;
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
            return this.isLastInfo;
        }

        public boolean isCanceled() {
            return this.isCanceled;
        }

        public boolean isWarning() {
            return this.isWarning;
        }

        public List<WarningArea> getZones() {
            return this.zones;
        }

        public List<WarningArea> getPrefectures() {
            return this.prefectures;
        }

        public List<WarningArea> getRegions() {
            return this.regions;
        }

        public Earthquake getEarthquake() {
            return this.earthquake;
        }

        @Nullable
        public Intensity getIntensity() {
            return this.intensity;
        }

        public String getText() {
            return this.text;
        }

        public Comments getComments() {
            return this.comments;
        }

        public static class WarningArea {

            private WarningAreaKind kind;
            private String code;
            private String name;

            public WarningAreaKind getKind() {
                return this.kind;
            }

            public String getCode() {
                return this.code;
            }

            public String getName() {
                return this.name;
            }

            public static class WarningAreaKind {

                private WarningAreaKindLastKind lastKind;
                private String code;
                private String name;

                public WarningAreaKindLastKind getLastKind() {
                    return this.lastKind;
                }

                public String getCode() {
                    return this.code;
                }

                public String getName() {
                    return this.name;
                }

                public static class WarningAreaKindLastKind {

                    private String code;
                    private String name;

                    public String getCode() {
                        return this.code;
                    }

                    public String getName() {
                        return this.name;
                    }

                    @Override
                    public String toString() {
                        return "WarningAreaKindLastKind{" +
                                "code='" + this.code + '\'' +
                                ", name='" + this.name + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "WarningAreaKind{" +
                            "lastKind=" + this.lastKind +
                            ", code='" + this.code + '\'' +
                            ", name='" + this.name + '\'' +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "WarningArea{" +
                        "kind=" + this.kind +
                        ", code='" + this.code + '\'' +
                        ", name='" + this.name + '\'' +
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
                return this.originTime;
            }

            public String getArrivalTime() {
                return this.arrivalTime;
            }

            public String getCondition() {
                return this.condition;
            }

            public EarthquakeHypocenter getHypocenter() {
                return this.hypocenter;
            }

            public EarthquakeMagnitude getMagnitude() {
                return this.magnitude;
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
                    return this.coordinate;
                }

                public Depth getDepth() {
                    return this.depth;
                }

                public EarthquakeHypocenterReduce getReduce() {
                    return this.reduce;
                }

                public String getLandOrSea() {
                    return this.landOrSea;
                }

                public EarthquakeHypocenterAccuracy getAccuracy() {
                    return this.accuracy;
                }

                public String getCode() {
                    return this.code;
                }

                public String getName() {
                    return this.name;
                }

                public static class Coordinate {

                    private LatitudeLongitude latitude;
                    private LatitudeLongitude longitude;
                    private Height height;
                    private String geodeticSystem;
                    private String condition;

                    public LatitudeLongitude getLatitude() {
                        return this.latitude;
                    }

                    public LatitudeLongitude getLongitude() {
                        return this.longitude;
                    }

                    public Height getHeight() {
                        return this.height;
                    }

                    public String getGeodeticSystem() {
                        return this.geodeticSystem;
                    }

                    public String getCondition() {
                        return this.condition;
                    }

                    public static class LatitudeLongitude {

                        private String text;
                        private String value;

                        public String getText() {
                            return this.text;
                        }

                        public String getValue() {
                            return this.value;
                        }

                        @Override
                        public String toString() {
                            return "LatitudeLongitude{" +
                                    "text='" + this.text + '\'' +
                                    ", value='" + this.value + '\'' +
                                    '}';
                        }
                    }

                    public static class Height {

                        private String type;
                        private String unit;
                        private String value;

                        public String getType() {
                            return this.type;
                        }

                        public String getUnit() {
                            return this.unit;
                        }

                        public String getValue() {
                            return this.value;
                        }

                        @Override
                        public String toString() {
                            return "Height{" +
                                    "type='" + this.type + '\'' +
                                    ", unit='" + this.unit + '\'' +
                                    ", value='" + this.value + '\'' +
                                    '}';
                        }
                    }

                    @Override
                    public String toString() {
                        return "Coordinate{" +
                                "latitude=" + this.latitude +
                                ", longitude=" + this.longitude +
                                ", height=" + this.height +
                                ", geodeticSystem='" + this.geodeticSystem + '\'' +
                                ", condition='" + this.condition + '\'' +
                                '}';
                    }
                }

                public static class Depth {

                    private String type;
                    private String unit;
                    private String value;
                    private String condition;

                    public String getType() {
                        return this.type;
                    }

                    public String getUnit() {
                        return this.unit;
                    }

                    public String getValue() {
                        return this.value;
                    }

                    public String getCondition() {
                        return this.condition;
                    }

                    @Override
                    public String toString() {
                        return "Depth{" +
                                "type='" + this.type + '\'' +
                                ", unit='" + this.unit + '\'' +
                                ", value='" + this.value + '\'' +
                                ", condition='" + this.condition + '\'' +
                                '}';
                    }
                }

                public static class EarthquakeHypocenterReduce {

                    private String code;
                    private String name;

                    public String getCode() {
                        return this.code;
                    }

                    public String getName() {
                        return this.name;
                    }

                    @Override
                    public String toString() {
                        return "EarthquakeHypocenterReduce{" +
                                "code='" + this.code + '\'' +
                                ", name='" + this.name + '\'' +
                                '}';
                    }
                }

                public static class EarthquakeHypocenterAccuracy {

                    private List<String> epicenters;
                    private String depth;
                    private String magnitudeCalculation;
                    private String numberOfMagnitudeCalculation;

                    public List<String> getEpicenters() {
                        return this.epicenters;
                    }

                    public String getDepth() {
                        return this.depth;
                    }

                    public String getMagnitudeCalculation() {
                        return this.magnitudeCalculation;
                    }

                    public String getNumberOfMagnitudeCalculation() {
                        return this.numberOfMagnitudeCalculation;
                    }

                    @Override
                    public String toString() {
                        return "EarthquakeHypocenterAccuracy{" +
                                "epicenters=" + this.epicenters +
                                ", depth='" + this.depth + '\'' +
                                ", magnitudeCalculation='" + this.magnitudeCalculation + '\'' +
                                ", numberOfMagnitudeCalculation='" + this.numberOfMagnitudeCalculation + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "EarthquakeHypocenter{" +
                            "coordinate=" + this.coordinate +
                            ", depth=" + this.depth +
                            ", reduce=" + this.reduce +
                            ", landOrSea='" + this.landOrSea + '\'' +
                            ", accuracy=" + this.accuracy +
                            ", code='" + this.code + '\'' +
                            ", name='" + this.name + '\'' +
                            '}';
                }
            }

            public static class EarthquakeMagnitude {

                private String type;
                private String unit;
                private String value;
                private String condition;

                public String getType() {
                    return this.type;
                }

                public String getUnit() {
                    return this.unit;
                }

                public String getValue() {
                    return this.value;
                }

                public String getCondition() {
                    return this.condition;
                }

                @Override
                public String toString() {
                    return "EarthquakeMagnitude{" +
                            "type='" + this.type + '\'' +
                            ", unit='" + this.unit + '\'' +
                            ", value='" + this.value + '\'' +
                            ", condition='" + this.condition + '\'' +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Earthquake{" +
                        "originTime='" + this.originTime + '\'' +
                        ", arrivalTime='" + this.arrivalTime + '\'' +
                        ", condition='" + this.condition + '\'' +
                        ", hypocenter=" + this.hypocenter +
                        ", magnitude=" + this.magnitude +
                        '}';
            }
        }

        public static class Intensity {

            private IntensityForecastMaxInt forecastMaxInt;
            private IntensityForecastLgMaxInt forecastMaxLgInt;
            private IntensityAppendix appendix;
            private List<IntensityRegionReached> regions;

            public IntensityForecastMaxInt getForecastMaxInt() {
                return this.forecastMaxInt;
            }

            public IntensityForecastLgMaxInt getForecastMaxLgInt() {
                return this.forecastMaxLgInt;
            }

            public IntensityAppendix getAppendix() {
                return this.appendix;
            }

            public List<IntensityRegionReached> getRegions() {
                return this.regions;
            }

            public static class IntensityForecastMaxInt {

                private String from;
                private String to;

                public String getFrom() {
                    return this.from;
                }

                public String getTo() {
                    return this.to;
                }

                @Override
                public String toString() {
                    return "IntensityForecastMaxInt{" +
                            "from='" + this.from + '\'' +
                            ", to='" + this.to + '\'' +
                            '}';
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;

                    IntensityForecastMaxInt that = (IntensityForecastMaxInt) o;

                    if (!this.from.equals(that.from)) return false;
                    return this.to.equals(that.to);
                }

                @Override
                public int hashCode() {
                    int result = this.from.hashCode();
                    result = 31 * result + this.to.hashCode();
                    return result;
                }
            }

            public static class IntensityForecastLgMaxInt {

                private String from;
                private String to;

                public String getFrom() {
                    return this.from;
                }

                public String getTo() {
                    return this.to;
                }

                @Override
                public String toString() {
                    return "IntensityForecastLgMaxInt{" +
                            "from='" + this.from + '\'' +
                            ", to='" + this.to + '\'' +
                            '}';
                }
            }

            public static class IntensityAppendix {

                private String maxIntChange;
                private String maxLgIntChange;
                private String maxIntChangeReason;

                public String getMaxIntChange() {
                    return this.maxIntChange;
                }

                public String getMaxLgIntChange() {
                    return this.maxLgIntChange;
                }

                public String getMaxIntChangeReason() {
                    return this.maxIntChangeReason;
                }

                @Override
                public String toString() {
                    return "IntensityAppendix{" +
                            "maxIntChange='" + this.maxIntChange + '\'' +
                            ", maxLgIntChange='" + this.maxLgIntChange + '\'' +
                            ", maxIntChangeReason='" + this.maxIntChangeReason + '\'' +
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
                    return this.condition;
                }

                public IntensityForecastMaxInt getForecastMaxInt() {
                    return this.forecastMaxInt;
                }

                public IntensityForecastLgMaxInt getForecastMaxLgInt() {
                    return this.forecastMaxLgInt;
                }

                public boolean isPlum() {
                    return this.isPlum;
                }

                public boolean isWarning() {
                    return this.isWarning;
                }

                public IntensityRegionKind getKind() {
                    return this.kind;
                }

                public String getCode() {
                    return this.code;
                }

                public String getName() {
                    return this.name;
                }

                public static class IntensityRegionKind {

                    private String code;
                    private String name;

                    public String getCode() {
                        return this.code;
                    }

                    public String getName() {
                        return this.name;
                    }

                    @Override
                    public String toString() {
                        return "IntensityRegionKind{" +
                                "code='" + this.code + '\'' +
                                ", name='" + this.name + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "IntensityRegionReached{" +
                            "condition='" + this.condition + '\'' +
                            ", forecastMaxInt=" + this.forecastMaxInt +
                            ", forecastMaxLgInt=" + this.forecastMaxLgInt +
                            ", isPlum=" + this.isPlum +
                            ", isWarning=" + this.isWarning +
                            ", kind=" + this.kind +
                            ", code='" + this.code + '\'' +
                            ", name='" + this.name + '\'' +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Intensity{" +
                        "forecastMaxInt=" + this.forecastMaxInt +
                        ", forecastMaxLgInt=" + this.forecastMaxLgInt +
                        ", appendix=" + this.appendix +
                        ", regions=" + this.regions +
                        '}';
            }
        }

        public static class Comments {

            private String free;
            private WarningComments warning;

            public String getFree() {
                return this.free;
            }

            public WarningComments getWarning() {
                return this.warning;
            }

            public static class WarningComments {

                private String text;
                private List<String> codes;

                public String getText() {
                    return this.text;
                }

                public List<String> getCodes() {
                    return this.codes;
                }

                @Override
                public String toString() {
                    return "WarningComments{" +
                            "text='" + this.text + '\'' +
                            ", codes=" + this.codes +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Comments{" +
                        "free='" + this.free + '\'' +
                        ", warning=" + this.warning +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Body{" +
                    "isLastInfo=" + this.isLastInfo +
                    ", isCanceled=" + this.isCanceled +
                    ", isWarning=" + this.isWarning +
                    ", zones=" + this.zones +
                    ", prefectures=" + this.prefectures +
                    ", regions=" + this.regions +
                    ", earthquake=" + this.earthquake +
                    ", intensity=" + this.intensity +
                    ", text='" + this.text + '\'' +
                    ", comments=" + this.comments +
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

    public boolean hasWarningUpdate() {
        if (!getBody().isWarning())
            return false;
        for (Body.WarningArea region : getBody().getRegions()) {
            if (region.getKind().getLastKind().getCode().equals("00"))
                return true;
        }
        return false;
    }

    @Override
    public MessageCreateSpec createMessage(final String lang) {
        return MessageCreateSpec.builder().addEmbed(createEmbed(lang, I18nEmbedCreateSpec.builder(lang))).build();
    }

    @Override
    public DiscordWebhook createWebhook(final String lang) {
        return DiscordWebhook.builder().addEmbed(createEmbed(lang, I18nDiscordEmbed.builder(lang))).build();
    }

    public <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        if (this.getBody().isCanceled()) {
            if (isConcurrent())
                builder.title("eewbot.eew.eewcancel.concurrent", getConcurrentIndex());
            else
                builder.title("eewbot.eew.eewcancel");
            return builder.timestamp(FORMAT.parse(this.getReportDateTime(), Instant::from))
                    .description(this.getBody().getText())
                    .color(Color.YELLOW)
                    .footer(String.join(" ", this.getPublishingOffice()), null)
                    .build();
        }

        if (getBody().isWarning()) {
            if (getBody().isLastInfo()) {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewalert.final.concurrent", getConcurrentIndex());
                else
                    builder.title("eewbot.eew.eewalert.final");
            } else {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewalert.num.concurrent", getConcurrentIndex(), this.getSerialNo());
                else
                    builder.title("eewbot.eew.eewalert.num", this.getSerialNo());
            }
            builder.color(Color.RED);
        } else {
            if (getBody().isLastInfo()) {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewprediction.final.concurrent", getConcurrentIndex());
                else
                    builder.title("eewbot.eew.eewprediction.final");
            } else {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewprediction.num.concurrent", getConcurrentIndex(), this.getSerialNo());
                else
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
            if (this.getBody().getIntensity() != null) {
                builder.addField("eewbot.eew.forecastseismicintensity",
                        SeismicIntensity.get(this.getBody().getIntensity().getForecastMaxInt().getFrom()).getSimple(),
                        false);
            }
        } else if (this.getBody().getIntensity() != null && this.getBody().getIntensity().getRegions() != null) {
            if (this.getBody().getIntensity().getRegions().isEmpty()) {
                builder.addField("eewbot.eew.plumseismicintensityplus", "eewbot.eew.near", false,
                        SeismicIntensity.get(this.getBody().getIntensity().getForecastMaxInt().getFrom()).getSimple(),
                        getBody().getEarthquake().getHypocenter().getName());
            } else {
                this.getBody().getIntensity().getRegions().stream()
                        .filter(Body.Intensity.IntensityRegionReached::isPlum)
                        .collect(Collectors.groupingBy(Body.Intensity.IntensityRegionReached::getForecastMaxInt,
                                Collectors.mapping(Body.Intensity.IntensityRegionReached::getName, Collectors.joining("　"))))
                        .entrySet()
                        .stream()
                        .sorted(Comparator.comparing(entry -> SeismicIntensity.get(entry.getKey().getFrom()), Comparator.reverseOrder()))
                        .forEach(entry -> {
                            if (entry.getKey().getTo().equals("over")) {
                                builder.addField("eewbot.eew.plumseismicintensityplus",
                                        entry.getValue(), false, SeismicIntensity.get(entry.getKey().getFrom()).getSimple());
                            } else {
                                builder.addField("eewbot.eew.plumseismicintensity",
                                        entry.getValue(), false, SeismicIntensity.get(entry.getKey().getTo()).getSimple());
                            }
                        });
            }
        }

        if (getBody().isWarning()) {
            builder.addField("eewbot.eew.warningtext", this.getBody().getRegions().stream()
                    .map(Body.WarningArea::getName)
                    .collect(Collectors.joining(" ")), false);
        }

        if (!isAccurateEnough()) {
            builder.description("eewbot.eew.inaccurate");
        }
        builder.footer(String.join(" ", this.getPublishingOffice()), null);
        return builder.build();
    }

    @Override
    public String toString() {
        return "DmdataEEW{" +
                "body=" + this.body +
                '}';
    }

}
