package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE51 extends JMAReport {

    @JacksonXmlProperty(localName = "Body")
    private Body body;

    public Body getBody() {
        return this.body;
    }

    public static class Body {

        @JacksonXmlProperty(localName = "Intensity")
        private Intensity intensity;

        @JacksonXmlProperty(localName = "Comments")
        private Comment comments;

        public Intensity getIntensity() {
            return this.intensity;
        }

        public Comment getComment() {
            return this.comments;
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

                        public String getName() {
                            return this.name;
                        }

                        public String getCode() {
                            return this.code;
                        }

                        public String getMaxInt() {
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
    public <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        return null;
    }

    @Override
    public String toString() {
        return "VXSE51{" +
                "body=" + this.body +
                '}';
    }
}
