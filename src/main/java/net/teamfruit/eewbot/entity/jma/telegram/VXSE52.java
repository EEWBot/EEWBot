package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.common.Magnitude;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.Optional;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE52 extends JMAReport {

    @JacksonXmlProperty(localName = "Body")
    private Body body;

    public Body getBody() {
        return this.body;
    }

    public static class Body {

        @JacksonXmlProperty(localName = "Earthquake")
        private Earthquake earthquake;

        @JacksonXmlProperty(localName = "Comments")
        private Comment comments;

        @JacksonXmlProperty(localName = "Text")
        private @Nullable String text;

        public Earthquake getEarthquake() {
            return this.earthquake;
        }

        public Comment getComment() {
            return this.comments;
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
                private Area area;

                public Area getArea() {
                    return this.area;
                }

                public static class Area {

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

        @Override
        public String toString() {
            return "Body{" +
                    "earthquake=" + this.earthquake +
                    ", comments=" + this.comments +
                    '}';
        }

    }

    @Override
    @SuppressWarnings("NonAsciiCharacters")
    public <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        builder.title("eewbot.quakeinfo.epicenter.title");
        if (getHead().getInfoType() == JMAInfoType.取消) {
            builder.description("eewbot.quakeinfo.epicenter.cancel");
            builder.color(SeismicIntensity.UNKNOWN.getColor());
        } else {
            getHead().getTargetDateTime().ifPresent(time -> builder.description("eewbot.quakeinfo.desc", "<t:" + time.getEpochSecond() + ":f>"));
            builder.addField("eewbot.quakeinfo.field.epicenter", getBody().getEarthquake().getHypocenter().getArea().getName(), true);
            getBody().getEarthquake().getHypocenter().getArea().getCoordinate().getDepth().ifPresent(depth -> builder.addField("eewbot.quakeinfo.field.depth", depth, true));
            builder.addField("eewbot.quakeinfo.field.magnitude", getBody().getEarthquake().getMagnitude().getMagnitude(), true);
        }
        builder.footer(getControl().getPublishingOffice(), null);
        builder.timestamp(getHead().getReportDateTime());
        return builder.build();
    }

    @Override
    public String toString() {
        return "VXSE52{" +
                "body=" + this.body +
                ", control=" + this.control +
                ", head=" + this.head +
                '}';
    }
}
