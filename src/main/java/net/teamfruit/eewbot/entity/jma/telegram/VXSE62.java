package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.common.Magnitude;
import reactor.util.annotation.Nullable;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE62 extends JMAReport {

    @JacksonXmlProperty(localName = "Body")
    private Body body;

    public Body getBody() {
        return this.body;
    }

    public static class Body {

        @JacksonXmlProperty(localName = "Earthquake")
        private Earthquake earthquake;

        @JacksonXmlProperty(localName = "Intensity")
        private Intensity intensity;

        @JacksonXmlProperty(localName = "Comments")
        private Comment comments;

        public static class Earthquake {

            @JacksonXmlProperty(localName = "OriginTime")
            private String originTime;

            @JacksonXmlProperty(localName = "ArrivalTime")
            private String arrivalTime;

            @JacksonXmlProperty(localName = "Hypocenter")
            private Hypocenter hypocenter;

            @JacksonXmlProperty(localName = "Magnitude")
            private Magnitude magnitude;

            public String getOriginTime() {
                return this.originTime;
            }

            public String getArrivalTime() {
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

                public HypoArea getArea() {
                    return this.area;
                }

                public static class HypoArea {

                    @JacksonXmlProperty(localName = "Name")
                    private String name;

                    @JacksonXmlProperty(localName = "Code")
                    private String code;

                    @JacksonXmlProperty(localName = "Coordinate")
                    private Coordinate coordinate;

                    public String getName() {
                        return this.name;
                    }

                    public String getCode() {
                        return this.code;
                    }

                    public Coordinate getCoordinate() {
                        return this.coordinate;
                    }

                    @Override
                    public String toString() {
                        return "Area{" +
                                "name='" + this.name + '\'' +
                                ", code='" + this.code + '\'' +
                                ", coordinate='" + this.coordinate + '\'' +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "Hypocenter{" +
                            "area=" + this.area +
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
                private String maxInt;

                @JacksonXmlProperty(localName = "MaxLgInt")
                private String maxLgInt;

                @JacksonXmlProperty(localName = "LgCategory")
                private String lgCategory;

                @JacksonXmlProperty(localName = "Pref")
                @JacksonXmlElementWrapper(useWrapping = false)
                private List<IntensityPref> prefs;

                public String getMaxInt() {
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
                    private String maxInt;

                    @JacksonXmlProperty(localName = "MaxLgInt")
                    private String maxLgInt;

                    @JacksonXmlProperty(localName = "Area")
                    @JacksonXmlElementWrapper(useWrapping = false)
                    private List<IntensityArea> areas;

                    public String getName() {
                        return this.name;
                    }

                    public String getCode() {
                        return this.code;
                    }

                    public String getMaxInt() {
                        return this.maxInt;
                    }

                    public String getMaxLgInt() {
                        return this.maxLgInt;
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
                        private String maxInt;

                        @JacksonXmlProperty(localName = "MaxLgInt")
                        private String maxLgInt;

                        @JacksonXmlProperty(localName = "IntensityStation")
                        @JacksonXmlElementWrapper(useWrapping = false)
                        private List<IntensityStation> stations;

                        public static class IntensityStation {

                            @JacksonXmlProperty(localName = "Name")
                            private String name;

                            @JacksonXmlProperty(localName = "Code")
                            private String code;

                            @JacksonXmlProperty(localName = "Int")
                            private @Nullable String intensity;

                            @JacksonXmlProperty(localName = "LgInt")
                            private String lgInt;

                            @JacksonXmlProperty(localName = "LgIntPerPeriod")
                            @JacksonXmlElementWrapper(useWrapping = false)
                            private List<LgIntPerPeriod> lgIntPerPeriods;

                            @JacksonXmlProperty(localName = "Sva")
                            private float sva;

                            @JacksonXmlProperty(localName = "SvaPerPeriod")
                            @JacksonXmlElementWrapper(useWrapping = false)
                            private List<SvaPerPeriod> svaPerPeriods;

                            public String getName() {
                                return this.name;
                            }

                            public String getCode() {
                                return this.code;
                            }

                            public Optional<String> getInt() {
                                return Optional.ofNullable(this.intensity);
                            }

                            public String getLgInt() {
                                return this.lgInt;
                            }

                            public List<LgIntPerPeriod> getLgIntPerPeriods() {
                                return this.lgIntPerPeriods;
                            }

                            public float getSva() {
                                return this.sva;
                            }

                            public List<SvaPerPeriod> getSvaPerPeriods() {
                                return this.svaPerPeriods;
                            }

                            public static class LgIntPerPeriod {

                                @JacksonXmlText
                                private String value;

                                @JacksonXmlProperty(localName = "PeriodicBand", isAttribute = true)
                                private @Nullable Integer periodicBand;

                                @JacksonXmlProperty(localName = "Period", isAttribute = true)
                                private @Nullable Float period;

                                @JacksonXmlProperty(localName = "PeriodUnit", isAttribute = true)
                                private @Nullable String periodUnit;

                                public String getValue() {
                                    return this.value;
                                }

                                public Optional<Integer> getPeriodicBand() {
                                    return Optional.ofNullable(this.periodicBand);
                                }

                                public Optional<Float> getPeriod() {
                                    return Optional.ofNullable(this.period);
                                }

                                public Optional<String> getPeriodUnit() {
                                    return Optional.ofNullable(this.periodUnit);
                                }

                                @Override
                                public String toString() {
                                    return "LgIntPerPeriod{" +
                                            "value='" + this.value + '\'' +
                                            ", periodicBand=" + this.periodicBand +
                                            ", period=" + this.period +
                                            ", periodUnit='" + this.periodUnit + '\'' +
                                            '}';
                                }
                            }

                            public static class SvaPerPeriod {

                                @JacksonXmlText
                                private float value;

                                @JacksonXmlProperty(isAttribute = true)
                                private String unit;

                                @JacksonXmlProperty(localName = "PeriodicBand", isAttribute = true)
                                private @Nullable Integer periodicBand;

                                @JacksonXmlProperty(localName = "Period", isAttribute = true)
                                private @Nullable Float period;

                                @JacksonXmlProperty(localName = "PeriodUnit", isAttribute = true)
                                private @Nullable String periodUnit;

                                public float getValue() {
                                    return this.value;
                                }

                                public String getUnit() {
                                    return this.unit;
                                }

                                public Optional<Integer> getPeriodicBand() {
                                    return Optional.ofNullable(this.periodicBand);
                                }

                                public Optional<Float> getPeriod() {
                                    return Optional.ofNullable(this.period);
                                }

                                public Optional<String> getPeriodUnit() {
                                    return Optional.ofNullable(this.periodUnit);
                                }

                                @Override
                                public String toString() {
                                    return "SvaPerPeriod{" +
                                            "value=" + this.value +
                                            ", unit='" + this.unit + '\'' +
                                            ", periodicBand=" + this.periodicBand +
                                            ", period=" + this.period +
                                            ", periodUnit='" + this.periodUnit + '\'' +
                                            '}';
                                }
                            }

                            @Override
                            public String toString() {
                                return "IntensityStation{" +
                                        "name='" + this.name + '\'' +
                                        ", code='" + this.code + '\'' +
                                        ", intensity='" + this.intensity + '\'' +
                                        ", lgInt='" + this.lgInt + '\'' +
                                        ", lgIntPerPeriods=" + this.lgIntPerPeriods +
                                        ", sva=" + this.sva +
                                        ", svaPerPeriods=" + this.svaPerPeriods +
                                        '}';
                            }
                        }

                        @Override
                        public String toString() {
                            return "IntensityArea{" +
                                    "name='" + this.name + '\'' +
                                    ", code='" + this.code + '\'' +
                                    ", maxInt='" + this.maxInt + '\'' +
                                    ", maxLgInt='" + this.maxLgInt + '\'' +
                                    ", stations=" + this.stations +
                                    '}';
                        }
                    }

                    @Override
                    public String toString() {
                        return "IntensityPref{" +
                                "name='" + this.name + '\'' +
                                ", code='" + this.code + '\'' +
                                ", maxInt='" + this.maxInt + '\'' +
                                ", maxLgInt='" + this.maxLgInt + '\'' +
                                ", areas=" + this.areas +
                                '}';
                    }
                }

                @Override
                public String toString() {
                    return "IntensityDetail{" +
                            "maxInt='" + this.maxInt + '\'' +
                            ", maxLgInt='" + this.maxLgInt + '\'' +
                            ", lgCategory='" + this.lgCategory + '\'' +
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
                    ", comments=" + this.comments +
                    '}';
        }
    }

    @Override
    public MessageCreateSpec createMessage(String lang) {
        return null;
    }

    @Override
    public DiscordWebhook createWebhook(String lang) {
        return null;
    }

    @Override
    public String toString() {
        return "VXSE62{" +
                "body=" + this.body +
                '}';
    }
}
