package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.common.Magnitude;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import org.apache.commons.lang3.StringUtils;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE53 extends JMAReport implements QuakeInfo {

    @JacksonXmlProperty(localName = "Body")
    private Body body;

    public Body getBody() {
        return this.body;
    }

    public static class Body {

        @JacksonXmlProperty(localName = "Earthquake")
        private @Nullable Earthquake earthquake;

        @JacksonXmlProperty(localName = "Intensity")
        private @Nullable Intensity intensity;

        @JacksonXmlProperty(localName = "Comments")
        private @Nullable Comment comments;

        @JacksonXmlProperty(localName = "Text")
        private @Nullable String text;

        public Optional<Earthquake> getEarthquake() {
            return Optional.ofNullable(this.earthquake);
        }

        public Optional<Intensity> getIntensity() {
            return Optional.ofNullable(this.intensity);
        }

        public Optional<Comment> getComments() {
            return Optional.ofNullable(this.comments);
        }

        public Optional<String> getText() {
            return Optional.ofNullable(this.text);
        }

        public static class Earthquake {

            @JacksonXmlProperty(localName = "OriginTime")
            private Instant originTime;

            @JacksonXmlProperty(localName = "ArrivalTime")
            private Instant arrivalTime;

            @JacksonXmlProperty(localName = "Hypocenter")
            private Hypocenter hypocenter;

            @JacksonXmlProperty(localName = "Magnitude")
            private Magnitude magnitude;

            public Instant getOriginTime() {
                return this.originTime;
            }

            public Instant getArrivalTime() {
                return this.arrivalTime;
            }

            public Hypocenter getHypocenter() {
                return this.hypocenter;
            }

            public Magnitude getMagnitude() {
                return this.magnitude;
            }

            public static class Hypocenter {

                @JacksonXmlProperty(localName = "Area")
                private HypoArea area;

                @JacksonXmlProperty(localName = "Source")
                private @Nullable String source;

                public HypoArea getArea() {
                    return this.area;
                }

                public Optional<String> getSource() {
                    return Optional.ofNullable(this.source);
                }

                public static class HypoArea {

                    @JacksonXmlProperty(localName = "Name")
                    private String name;

                    @JacksonXmlProperty(localName = "Code")
                    private String code;

                    @JacksonXmlProperty(localName = "Coordinate")
                    private Coordinate coordinate;

                    @JacksonXmlProperty(localName = "DetailedName")
                    private @Nullable String detailedName;

                    @JacksonXmlProperty(localName = "DetailedCode")
                    private @Nullable String detailedCode;

                    @JacksonXmlProperty(localName = "NameFromMark")
                    private @Nullable String nameFromMark;

                    @JacksonXmlProperty(localName = "MarkCode")
                    private @Nullable String markCode;

                    @JacksonXmlProperty(localName = "Direction")
                    private @Nullable String direction;

                    @JacksonXmlProperty(localName = "Distance")
                    private @Nullable String distance;

                    public String getName() {
                        return this.name;
                    }

                    public String getCode() {
                        return this.code;
                    }

                    public Coordinate getCoordinate() {
                        return this.coordinate;
                    }

                    public Optional<String> getDetailedName() {
                        return Optional.ofNullable(this.detailedName);
                    }

                    public Optional<String> getDetailedCode() {
                        return Optional.ofNullable(this.detailedCode);
                    }

                    public Optional<String> getNameFromMark() {
                        return Optional.ofNullable(this.nameFromMark);
                    }

                    public Optional<String> getMarkCode() {
                        return Optional.ofNullable(this.markCode);
                    }

                    public Optional<String> getDirection() {
                        return Optional.ofNullable(this.direction);
                    }

                    public Optional<String> getDistance() {
                        return Optional.ofNullable(this.distance);
                    }

                    @Override
                    public String toString() {
                        return "HypoArea{" +
                                "name='" + this.name + '\'' +
                                ", code='" + this.code + '\'' +
                                ", coordinate=" + this.coordinate +
                                ", detailedName='" + this.detailedName + '\'' +
                                ", detailedCode='" + this.detailedCode + '\'' +
                                ", nameFromMark='" + this.nameFromMark + '\'' +
                                ", markCode='" + this.markCode + '\'' +
                                ", direction='" + this.direction + '\'' +
                                ", distance='" + this.distance + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "Hypocenter{" +
                            "area=" + this.area +
                            ", source='" + this.source + '\'' +
                            '}';
                }
            }

            @Override
            public String toString() {
                return "Earthquake{" +
                        "originTime='" + this.originTime + '\'' +
                        ", arrivalTime='" + this.arrivalTime + '\'' +
                        ", hypocenter=" + this.hypocenter +
                        ", magnitude='" + this.magnitude + '\'' +
                        '}';
            }
        }

        public static class Intensity {

            @JacksonXmlProperty(localName = "Observation")
            private IntensityDetail observation;

            public IntensityDetail getObservation() {
                return this.observation;
            }

            @JsonIgnoreProperties("CodeDefine")
            public static class IntensityDetail {

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
                    private @Nullable SeismicIntensity maxInt;

                    @JacksonXmlProperty(localName = "Revise")
                    private @Nullable String revise;

                    @JacksonXmlProperty(localName = "Area")
                    @JacksonXmlElementWrapper(useWrapping = false)
                    private List<IntensityArea> areas;

                    public String getName() {
                        return this.name;
                    }

                    public String getCode() {
                        return this.code;
                    }

                    public Optional<SeismicIntensity> getMaxInt() {
                        return Optional.ofNullable(this.maxInt);
                    }

                    public Optional<String> getRevise() {
                        return Optional.ofNullable(this.revise);
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
                        private @Nullable SeismicIntensity maxInt;

                        @JacksonXmlProperty(localName = "Revise")
                        private @Nullable String revise;

                        @JacksonXmlProperty(localName = "City")
                        @JacksonXmlElementWrapper(useWrapping = false)
                        private List<IntensityCity> cities;

                        public String getName() {
                            return this.name;
                        }

                        public String getCode() {
                            return this.code;
                        }

                        public Optional<SeismicIntensity> getMaxInt() {
                            return Optional.ofNullable(this.maxInt);
                        }

                        public Optional<String> getRevise() {
                            return Optional.ofNullable(this.revise);
                        }

                        public List<IntensityCity> getCities() {
                            return this.cities;
                        }

                        public static class IntensityCity {

                            @JacksonXmlProperty(localName = "Name")
                            private String name;

                            @JacksonXmlProperty(localName = "Code")
                            private String code;

                            @JacksonXmlProperty(localName = "MaxInt")
                            private @Nullable SeismicIntensity maxInt;

                            @JacksonXmlProperty(localName = "Condition")
                            private @Nullable String condition;

                            @JacksonXmlProperty(localName = "Revise")
                            private @Nullable String revise;

                            @JacksonXmlProperty(localName = "IntensityStation")
                            @JacksonXmlElementWrapper(useWrapping = false)
                            private List<IntensityStation> stations;

                            public String getName() {
                                return this.name;
                            }

                            public String getCode() {
                                return this.code;
                            }

                            public Optional<SeismicIntensity> getMaxInt() {
                                return Optional.ofNullable(this.maxInt);
                            }

                            public Optional<String> getCondition() {
                                return Optional.ofNullable(this.condition);
                            }

                            public Optional<String> getRevise() {
                                return Optional.ofNullable(this.revise);
                            }

                            public List<IntensityStation> getStations() {
                                return this.stations;
                            }

                            public static class IntensityStation {

                                @JacksonXmlProperty(localName = "Name")
                                protected String name;

                                @JacksonXmlProperty(localName = "Code")
                                protected String code;

                                @JacksonXmlProperty(localName = "Int")
                                private String intensity;

                                @JacksonXmlProperty(localName = "Revise")
                                protected @Nullable String revise;

                                public String getName() {
                                    return this.name;
                                }

                                public String getCode() {
                                    return this.code;
                                }

                                public String getInt() {
                                    return this.intensity;
                                }

                                public Optional<String> getRevise() {
                                    return Optional.ofNullable(this.revise);
                                }

                                @Override
                                public String toString() {
                                    return "IntensityStation{" +
                                            "name='" + this.name + '\'' +
                                            ", code='" + this.code + '\'' +
                                            ", intensity='" + this.intensity + '\'' +
                                            ", revise='" + this.revise + '\'' +
                                            '}';
                                }
                            }

                            @Override
                            public String toString() {
                                return "IntensityCity{" +
                                        "name='" + this.name + '\'' +
                                        ", code='" + this.code + '\'' +
                                        ", maxInt='" + this.maxInt + '\'' +
                                        ", stations=" + this.stations +
                                        '}';
                            }
                        }

                        @Override
                        public String toString() {
                            return "IntensityArea{" +
                                    "name='" + this.name + '\'' +
                                    ", code='" + this.code + '\'' +
                                    ", maxInt='" + this.maxInt + '\'' +
                                    ", cities=" + this.cities +
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
                    "earthquake=" + this.earthquake +
                    ", intensity=" + this.intensity +
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
        if (getHead().getInfoType() == JMAInfoType.取消) {
            builder.title("eewbot.quakeinfo.detail.title");
            builder.description("eewbot.quakeinfo.detail.cancel");
            builder.color(SeismicIntensity.UNKNOWN.getColor());
        } else if (getHead().getTitle().equals("遠地地震に関する情報")) {
            getBody().getComments().flatMap(Comment::getFreeFormComment).filter(text -> text.contains("噴火が発生")).ifPresentOrElse(text -> {
                // 海外噴火
                builder.title("eewbot.quakeinfo.detail.eruption.title");
                getBody().getEarthquake().ifPresent(earthquake -> earthquake.getHypocenter().getArea().getDetailedName()
                        .ifPresentOrElse(detailedName -> builder.addField("eewbot.quakeinfo.field.area", detailedName, true),
                                () -> builder.addField("eewbot.quakeinfo.field.area", earthquake.getHypocenter().getArea().getName(), true)));
                builder.addField("", StringUtils.substringBefore(text, "（注"), false);
            }, () -> {
                // 海外地震
                builder.title("eewbot.quakeinfo.detail.overseas.title");
                getBody().getEarthquake().ifPresent(earthquake -> {
                    builder.description("eewbot.quakeinfo.detail.overseas.desc", "<t:" + earthquake.getOriginTime().getEpochSecond() + ":f>");
                    earthquake.getHypocenter().getArea().getDetailedName().ifPresentOrElse(detailedName -> builder.addField("eewbot.quakeinfo.field.epicenter", detailedName, true),
                            () -> builder.addField("eewbot.quakeinfo.field.epicenter", earthquake.getHypocenter().getArea().getName(), true));
                    builder.addField("eewbot.quakeinfo.field.magnitude", earthquake.getMagnitude().getMagnitude(), true);
                });
                getBody().getComments().flatMap(Comment::getFreeFormComment).ifPresent(freeFormComment -> builder.addField("", freeFormComment, false));
            });
            getBody().getComments().flatMap(Comment::getForecastComment).ifPresent(forecastComment -> builder.addField("", forecastComment.getText(), false));
        } else {
            builder.title("eewbot.quakeinfo.detail.title");
            getBody().getEarthquake().ifPresent(earthquake -> {
                builder.description("eewbot.quakeinfo.detail.desc", "<t:" + earthquake.getOriginTime().getEpochSecond() + ":f>");
                builder.addField("eewbot.quakeinfo.field.epicenter", earthquake.getHypocenter().getArea().getName(), true);
                earthquake.getHypocenter().getArea().getCoordinate().getDepth().ifPresent(depth -> builder.addField("eewbot.quakeinfo.field.depth", depth, true));
                builder.addField("eewbot.quakeinfo.field.magnitude", earthquake.getMagnitude().getMagnitude(), true);
            });
            getBody().getIntensity().ifPresent(intensity -> {
                builder.addField("eewbot.quakeinfo.field.maxintensity", intensity.getObservation().getMaxInt().getSimple(), true);
                builder.color(intensity.getObservation().getMaxInt().getColor());
            });
            getBody().getComments().ifPresent(comment -> {
                comment.getForecastComment().ifPresent(forecastComment -> builder.addField("", forecastComment.getText(), false));
                comment.getVarComment().ifPresent(varComment -> builder.addField("", varComment.getText().replace("＊印は気象庁以外の震度観測点についての情報です。", ""), false));
                comment.getFreeFormComment().ifPresent(freeFormComment -> builder.addField("", freeFormComment, false));
            });
        }
        builder.footer(getControl().getPublishingOffice(), null);
        builder.timestamp(getHead().getReportDateTime());
        return builder.build();
    }

    @Override
    public String toString() {
        return "VXSE53{" +
                "body=" + this.body +
                ", control=" + this.control +
                ", head=" + this.head +
                '}';
    }
}
