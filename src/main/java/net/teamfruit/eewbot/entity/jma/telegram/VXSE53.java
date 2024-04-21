package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.common.Magnitude;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import reactor.util.annotation.Nullable;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE53 extends JMAReport {

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

        public Earthquake getEarthquake() {
            return this.earthquake;
        }

        public Intensity getIntensity() {
            return this.intensity;
        }

        public Comment getComments() {
            return this.comments;
        }

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

                    @JacksonXmlProperty(localName = "DetailedName")
                    private @Nullable String detailedName;

                    @JacksonXmlProperty(localName = "DetailedCode")
                    private @Nullable String detailedCode;

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

                        @JacksonXmlProperty(localName = "City")
                        @JacksonXmlElementWrapper(useWrapping = false)
                        private List<IntensityCity> cities;

                        public String getName() {
                            return this.name;
                        }

                        public String getCode() {
                            return this.code;
                        }

                        public String getMaxInt() {
                            return this.maxInt;
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
                            private String maxInt;

                            @JacksonXmlProperty(localName = "IntensityStation")
                            @JacksonXmlElementWrapper(useWrapping = false)
                            private List<IntensityStation> stations;

                            public String getName() {
                                return this.name;
                            }

                            public String getCode() {
                                return this.code;
                            }

                            public String getMaxInt() {
                                return this.maxInt;
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
    public <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        return null;
    }

    @Override
    public String toString() {
        return "VXSE53{" +
                "body=" + this.body +
                '}';
    }
}
