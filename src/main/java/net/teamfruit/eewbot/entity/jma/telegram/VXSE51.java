package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import reactor.util.annotation.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE51 extends JMAReport implements QuakeInfo {

    @JacksonXmlProperty(localName = "Body")
    private Body body;

    public Body getBody() {
        return this.body;
    }

    public static class Body {

        @JacksonXmlProperty(localName = "Intensity")
        private @Nullable Intensity intensity;

        @JacksonXmlProperty(localName = "Comments")
        private @Nullable Comment comments;

        @JacksonXmlProperty(localName = "Text")
        private @Nullable String text;

        public Optional<Intensity> getIntensity() {
            return Optional.ofNullable(this.intensity);
        }

        public Optional<Comment> getComment() {
            return Optional.ofNullable(this.comments);
        }

        public Optional<String> getText() {
            return Optional.ofNullable(this.text);
        }

        public static class Intensity {

            @JacksonXmlProperty(localName = "Observation")
            private IntensityDetail observation;

            public IntensityDetail getObservation() {
                return this.observation;
            }

            @JsonIgnoreProperties("CodeDefine")
            public static class IntensityDetail {

                // ignore CodeDefine
                @JacksonXmlProperty(localName = "MaxInt")
                private SeismicIntensity maxInt;

                @JacksonXmlProperty(localName = "Pref")
                @JacksonXmlElementWrapper(useWrapping = false)
                private List<IntensityPref> prefs;

                public SeismicIntensity getMaxInt() {
                    return this.maxInt;
                }

                public List<IntensityPref> getPrefs() {
                    return this.prefs;
                }

                public static class IntensityPref {

                    @JacksonXmlProperty(localName = "Name")
                    private String name;

                    @JacksonXmlProperty(localName = "Code")
                    private String code;

                    @JacksonXmlProperty(localName = "MaxInt")
                    private SeismicIntensity maxInt;

                    @JacksonXmlProperty(localName = "Area")
                    @JacksonXmlElementWrapper(useWrapping = false)
                    private List<IntensityArea> areas;

                    public String getName() {
                        return this.name;
                    }

                    public String getCode() {
                        return this.code;
                    }

                    public SeismicIntensity getMaxInt() {
                        return this.maxInt;
                    }

                    public List<IntensityArea> getAreas() {
                        return this.areas;
                    }

                    public static class IntensityArea {

                        @JacksonXmlProperty(localName = "Name")
                        private String name;

                        @JacksonXmlProperty(localName = "Code")
                        private String code;

                        @JacksonXmlProperty(localName = "MaxInt")
                        private SeismicIntensity maxInt;

                        public String getName() {
                            return this.name;
                        }

                        public String getCode() {
                            return this.code;
                        }

                        public SeismicIntensity getMaxInt() {
                            return this.maxInt;
                        }

                        @Override
                        public String toString() {
                            return "IntensityArea{" +
                                    "name='" + this.name + '\'' +
                                    ", code='" + this.code + '\'' +
                                    ", maxInt='" + this.maxInt + '\'' +
                                    '}';
                        }
                    }

                    @Override
                    public String toString() {
                        return "IntensityPref{" +
                                "name='" + this.name + '\'' +
                                ", code='" + this.code + '\'' +
                                ", maxInt='" + this.maxInt + '\'' +
                                ", areas=" + this.areas +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "Observation{" +
                            "maxInt='" + this.maxInt + '\'' +
                            ", prefs=" + this.prefs +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Intensity{" +
                        "observation=" + this.observation +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Body{" +
                    "intensity=" + this.intensity +
                    ", comment=" + this.comments +
                    '}';
        }
    }

    @Override
    public Optional<SeismicIntensity> getMaxInt() {
        return getBody().getIntensity().map(intensity -> intensity.getObservation().getMaxInt());
    }

    @Override
    @SuppressWarnings("NonAsciiCharacters")
    public <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        builder.title("eewbot.quakeinfo.intensity.title");
        if (getHead().getInfoType() == JMAInfoType.取消) {
            builder.description("eewbot.quakeinfo.intensity.cancel");
            builder.color(SeismicIntensity.UNKNOWN.getColor());
        } else {
            getHead().getTargetDateTime().ifPresent(time -> builder.description("eewbot.quakeinfo.intensity.desc", "<t:" + time.getEpochSecond() + ":f>"));
            getBody().getIntensity().ifPresent(intensity -> {
                Map<SeismicIntensity, StringBuilder> intensityMap = new EnumMap<>(SeismicIntensity.class);
                intensity.getObservation().getPrefs()
                        .stream().flatMap(pref -> pref.getAreas().stream())
                        .forEach(area -> {
                            StringBuilder sb = intensityMap.computeIfAbsent(area.getMaxInt(), k -> new StringBuilder());
                            if (sb.length() > 0)
                                sb.append("  ");
                            sb.append(area.getName());
                        });

                SeismicIntensity[] intensities = SeismicIntensity.values();
                for (int i = intensities.length - 1; i >= 0; i--) {
                    SeismicIntensity line = intensities[i];
                    StringBuilder sb = intensityMap.get(line);
                    if (sb != null) {
                        builder.addField("eewbot.quakeinfo.field.intensity", sb.toString(), false, line.getSimple());
                    }
                }
                builder.color(intensity.getObservation().getMaxInt().getColor());
            });
            getBody().getComment().ifPresent(comment -> {
                comment.getForecastComment().ifPresent(forecastComment -> builder.addField("", forecastComment.getText(), false));
                comment.getFreeFormComment().ifPresent(freeFormComment -> builder.addField("", freeFormComment, false));
            });
        }
        builder.footer(getControl().getPublishingOffice(), null);
        builder.timestamp(getHead().getReportDateTime());
        return builder.build();
    }

    @Override
    public String toString() {
        return "VXSE51{" +
                "body=" + this.body +
                ", control=" + this.control +
                ", head=" + this.head +
                '}';
    }
}
