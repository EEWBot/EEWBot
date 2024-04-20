package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.common.Magnitude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VXSE61 extends JMAReport {

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

        public Earthquake getEarthquake() {
            return this.earthquake;
        }

        public Comment getComment() {
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
                    @JacksonXmlElementWrapper(useWrapping = false)
                    private List<Coordinate> coordinate;

                    public String getName() {
                        return this.name;
                    }

                    public String getCode() {
                        return this.code;
                    }

                    public List<Coordinate> getCoordinate() {
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
    public MessageCreateSpec createMessage(String lang) {
        return null;
    }

    @Override
    public DiscordWebhook createWebhook(String lang) {
        return null;
    }

    @Override
    public String toString() {
        return "VXSE61{" +
                "body=" + this.body +
                '}';
    }
}
