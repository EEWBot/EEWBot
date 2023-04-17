package net.teamfruit.eewbot.entity;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.gateway.QuakeInfoGateway;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@JacksonXmlRootElement(localName = "Root")
public class DetailQuakeInfo implements Entity {

    public static final ObjectMapper DETAIL_QUAKE_INFO_MAPPER = XmlMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new SimpleModule().addDeserializer(LocalDateTime.class, new DateDeserializer())
                    .addDeserializer(SeismicIntensity.class, new SeismicIntensityDeserializer()))
            .build();

    private LocalDateTime timestamp;
    private Earthquake earthQuake;

    @JacksonXmlProperty(localName = "Timestamp")
    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @JacksonXmlProperty(localName = "Earthquake")
    public Earthquake getEarthquake() {
        return this.earthQuake;
    }

    public void setEarthquake(final Earthquake earthQuake) {
        this.earthQuake = earthQuake;
    }

    public static class Earthquake {

        private String id;
        private LocalDateTime time;
        private SeismicIntensity intensity;
        private String epicenter;
        private String lat;
        private String lon;
        private String magnitude;
        private String depth;

        private String detail;
        private String local;
        private String global;

        private Relative relative;

        @JacksonXmlProperty(localName = "Id", isAttribute = true)
        public String getId() {
            return this.id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        @JacksonXmlProperty(localName = "Time", isAttribute = true)
        public LocalDateTime getTime() {
            return this.time;
        }

        public void setTime(final LocalDateTime time) {
            this.time = time;
        }

        @JacksonXmlProperty(localName = "Intensity", isAttribute = true)
        public SeismicIntensity getIntensity() {
            return this.intensity;
        }

        public void setIntensity(final SeismicIntensity intensity) {
            this.intensity = intensity;
        }

        @JacksonXmlProperty(localName = "Epicenter", isAttribute = true)
        public String getEpicenter() {
            return this.epicenter;
        }

        public void setEpicenter(final String epicenter) {
            this.epicenter = epicenter;
        }

        @JacksonXmlProperty(localName = "Latitude", isAttribute = true)
        public String getLat() {
            return this.lat;
        }

        public void setLat(final String lat) {
            this.lat = lat;
        }

        @JacksonXmlProperty(localName = "Longitude", isAttribute = true)
        public String getLon() {
            return this.lon;
        }

        public void setLon(final String lon) {
            this.lon = lon;
        }

        @JacksonXmlProperty(localName = "Magnitude", isAttribute = true)
        public String getMagnitude() {
            return this.magnitude;
        }

        public void setMagnitude(final String magnitude) {
            this.magnitude = magnitude;
        }

        @JacksonXmlProperty(localName = "Depth", isAttribute = true)
        public String getDepth() {
            return this.depth;
        }

        public void setDepth(final String depth) {
            this.depth = depth;
        }

        @JacksonXmlProperty(localName = "Detail")
        public String getDetail() {
            return this.detail;
        }

        public void setDetail(final String detail) {
            this.detail = detail;
        }

        @JacksonXmlProperty(localName = "Local")
        public String getLocal() {
            return this.local;
        }

        public void setLocal(final String local) {
            this.local = local;
        }

        @JacksonXmlProperty(localName = "Global")
        public String getGlobal() {
            return this.global;
        }

        public void setGlobal(final String global) {
            this.global = global;
        }

        @JacksonXmlProperty(localName = "Relative")
        public Relative getRelative() {
            return this.relative;
        }

        public void setRelative(final Relative relative) {
            this.relative = relative;
        }

        public static class Relative {

            private List<Group> groups;

            @JacksonXmlProperty(localName = "Group")
            @JacksonXmlElementWrapper(useWrapping = false)
            public List<Group> getGroups() {
                return this.groups;
            }

            public void setGroups(final List<Group> groups) {
                this.groups = groups;
            }

            public static class Group {

                private String intensity;
                private List<Area> areas;

                @JacksonXmlProperty(localName = "Intensity", isAttribute = true)
                public String getIntensity() {
                    return this.intensity;
                }

                public void setIntensity(final String intensity) {
                    this.intensity = intensity;
                }

                @JacksonXmlProperty(localName = "Area")
                @JacksonXmlElementWrapper(useWrapping = false)
                public List<Area> getAreas() {
                    return this.areas;
                }

                public void setAreas(final List<Area> areas) {
                    this.areas = areas;
                }

                public static class Area {

                    private String name;

                    @JacksonXmlProperty(localName = "Name", isAttribute = true)
                    public String getName() {
                        return this.name;
                    }

                    public void setName(final String name) {
                        this.name = name;
                    }

                }
            }
        }
    }

    public static class DateDeserializer extends StdDeserializer<LocalDateTime> {

        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

        public DateDeserializer() {
            this(null);
        }

        protected DateDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public LocalDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            String date = jsonParser.getText();
            return LocalDateTime.parse(date, formatter);
        }
    }

    public static class SeismicIntensityDeserializer extends StdDeserializer<SeismicIntensity> {

        public SeismicIntensityDeserializer() {
            this(null);
        }

        protected SeismicIntensityDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public SeismicIntensity deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            String intensity = jsonParser.getText();
            return SeismicIntensity.get(intensity).orElse(null);
        }
    }

    @Override
    public MessageCreateSpec createMessage(final String lang) {
        return MessageCreateSpec.builder()
                .addEmbed(createEmbed(lang)).build();
    }

    public EmbedCreateSpec createEmbed(String lang) {
        return I18nEmbedCreateSpec.builder(lang)
                .title("eewbot.quakeinfo.title")
                .addField("eewbot.quakeinfo.epicenter", getEarthquake().getEpicenter(), true)
                .addField("eewbot.quakeinfo.depth", getEarthquake().getDepth(), true)
                .addField("eewbot.quakeinfo.magnitude", getEarthquake().getMagnitude(), true)
                .addField("eewbot.quakeinfo.seismicintensity", getEarthquake().getIntensity().getSimple(), false)
                .image(QuakeInfoGateway.REMOTE_ROOT + getEarthquake().getDetail())
                .color(getEarthquake().getIntensity().getColor())
                .timestamp(getEarthquake().getTime().atZone(TimeProvider.ZONE_ID).toInstant())
                .build();
    }
}
