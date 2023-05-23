package net.teamfruit.eewbot.entity;

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
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@JacksonXmlRootElement(localName = "Root")
public class DetailQuakeInfo implements Entity {

    public static final ObjectMapper DETAIL_QUAKE_INFO_MAPPER = XmlMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new SimpleModule()
                    .addDeserializer(LocalDateTime.class, new DateDeserializer())
                    .addDeserializer(SeismicIntensity.class, new SeismicIntensityDeserializer()))
            .build();

    @JacksonXmlProperty(localName = "Timestamp")
    private LocalDateTime timestamp;
    @JacksonXmlProperty(localName = "Earthquake")
    private Earthquake earthQuake;

    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Earthquake getEarthquake() {
        return this.earthQuake;
    }

    public void setEarthquake(final Earthquake earthQuake) {
        this.earthQuake = earthQuake;
    }

    @Override
    public String toString() {
        return "DetailQuakeInfo{" +
                "timestamp=" + timestamp +
                ", earthQuake=" + earthQuake +
                '}';
    }

    public static class Earthquake {

        @JacksonXmlProperty(localName = "Id", isAttribute = true)
        private String id;

        @JacksonXmlProperty(localName = "Time", isAttribute = true)
        private LocalDateTime time;

        @JacksonXmlProperty(localName = "Intensity", isAttribute = true)
        private SeismicIntensity intensity;

        @JacksonXmlProperty(localName = "Epicenter", isAttribute = true)
        private String epicenter;

        @JacksonXmlProperty(localName = "Latitude", isAttribute = true)
        private String lat;

        @JacksonXmlProperty(localName = "Longitude", isAttribute = true)
        private String lon;

        @JacksonXmlProperty(localName = "Magnitude", isAttribute = true)
        private String magnitude;

        @JacksonXmlProperty(localName = "Depth", isAttribute = true)
        private String depth;

        @JacksonXmlProperty(localName = "Detail")
        private String detail;

        @JacksonXmlProperty(localName = "Local")
        private String local;

        @JacksonXmlProperty(localName = "Global")
        private String global;

        @JacksonXmlProperty(localName = "Relative")
        private Relative relative;

        public String getId() {
            return this.id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        public LocalDateTime getTime() {
            return this.time;
        }

        public void setTime(final LocalDateTime time) {
            this.time = time;
        }

        public SeismicIntensity getIntensity() {
            return this.intensity;
        }

        public void setIntensity(final SeismicIntensity intensity) {
            this.intensity = intensity;
        }

        public String getEpicenter() {
            return this.epicenter;
        }

        public void setEpicenter(final String epicenter) {
            this.epicenter = epicenter;
        }

        public String getLat() {
            return this.lat;
        }

        public void setLat(final String lat) {
            this.lat = lat;
        }

        public String getLon() {
            return this.lon;
        }

        public void setLon(final String lon) {
            this.lon = lon;
        }

        public String getMagnitude() {
            return this.magnitude;
        }

        public void setMagnitude(final String magnitude) {
            this.magnitude = magnitude;
        }

        public String getDepth() {
            return this.depth;
        }

        public void setDepth(final String depth) {
            this.depth = depth;
        }

        public String getDetail() {
            return this.detail;
        }

        public void setDetail(final String detail) {
            this.detail = detail;
        }

        public String getLocal() {
            return this.local;
        }

        public void setLocal(final String local) {
            this.local = local;
        }

        public String getGlobal() {
            return this.global;
        }

        public void setGlobal(final String global) {
            this.global = global;
        }

        public Relative getRelative() {
            return this.relative;
        }

        public void setRelative(final Relative relative) {
            this.relative = relative;
        }

        @Override
        public String toString() {
            return "Earthquake{" +
                    "id='" + id + '\'' +
                    ", time=" + time +
                    ", intensity=" + intensity +
                    ", epicenter='" + epicenter + '\'' +
                    ", lat='" + lat + '\'' +
                    ", lon='" + lon + '\'' +
                    ", magnitude='" + magnitude + '\'' +
                    ", depth='" + depth + '\'' +
                    ", detail='" + detail + '\'' +
                    ", local='" + local + '\'' +
                    ", global='" + global + '\'' +
                    ", relative=" + relative +
                    '}';
        }

        public static class Relative {

            @JacksonXmlProperty(localName = "Group")
            @JacksonXmlElementWrapper(useWrapping = false)
            private List<Group> groups;

            public List<Group> getGroups() {
                return this.groups;
            }

            public void setGroups(final List<Group> groups) {
                this.groups = groups;
            }

            @Override
            public String toString() {
                return "Relative{" +
                        "groups=" + groups +
                        '}';
            }

            public static class Group {

                @JacksonXmlProperty(localName = "Intensity", isAttribute = true)
                private String intensity;

                @JacksonXmlProperty(localName = "Area")
                @JacksonXmlElementWrapper(useWrapping = false)
                private List<Area> areas;

                public String getIntensity() {
                    return this.intensity;
                }

                public void setIntensity(final String intensity) {
                    this.intensity = intensity;
                }

                public List<Area> getAreas() {
                    return this.areas;
                }

                public void setAreas(final List<Area> areas) {
                    this.areas = areas;
                }

                @Override
                public String toString() {
                    return "Group{" +
                            "intensity='" + intensity + '\'' +
                            ", areas=" + areas +
                            '}';
                }

                public static class Area {

                    @JacksonXmlProperty(localName = "Name", isAttribute = true)
                    private String name;

                    public String getName() {
                        return this.name;
                    }

                    public void setName(final String name) {
                        this.name = name;
                    }

                    @Override
                    public String toString() {
                        return "Area{" +
                                "name='" + name + '\'' +
                                '}';
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
        public LocalDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
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
        public SeismicIntensity deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            String intensity = jsonParser.getText();
            return SeismicIntensity.get(intensity).orElse(null);
        }
    }

    public enum Type {
        INTENSITY,
        EPICENTER,
        DETAIL
    }

    public Type getType() {
        Earthquake eq = getEarthquake();
        if (StringUtils.isAllEmpty(eq.getEpicenter(), eq.getLat(), eq.getLon(), eq.getMagnitude(), eq.getDepth()))
            return Type.INTENSITY;
        if (StringUtils.isAllEmpty(eq.getLocal(), eq.getGlobal()))
            return Type.EPICENTER;
        return Type.DETAIL;
    }

    @Override
    public MessageCreateSpec createMessage(final String lang) {
        return MessageCreateSpec.builder().addEmbed(createEmbed(lang)).build();
    }

    public EmbedCreateSpec createEmbed(String lang) {
        Earthquake eq = getEarthquake();
        Type type = getType();
        I18nEmbedCreateSpec.Builder builder = I18nEmbedCreateSpec.builder(lang);

        switch (type) {
            case INTENSITY:
                builder.title("eewbot.quakeinfo.intensity.title");
                addIntensityGroupFields(builder, eq);
                break;
            case EPICENTER:
                builder.title("eewbot.quakeinfo.epicenter.title")
                        .addField("eewbot.quakeinfo.field.epicenter", eq.getEpicenter(), true)
                        .addField("eewbot.quakeinfo.field.depth", eq.getDepth(), true)
                        .addField("eewbot.quakeinfo.field.magnitude", eq.getMagnitude(), true);
                addIntensityGroupFields(builder, eq);
                break;
            case DETAIL:
            default:
                builder.title("eewbot.quakeinfo.detail.title")
                        .addField("eewbot.quakeinfo.field.epicenter", eq.getEpicenter(), true)
                        .addField("eewbot.quakeinfo.field.depth", eq.getDepth(), true)
                        .addField("eewbot.quakeinfo.field.magnitude", eq.getMagnitude(), true)
                        .addField("eewbot.quakeinfo.field.maxintensity", eq.getIntensity().getSimple(), false);
        }

        return builder.image(QuakeInfoGateway.REMOTE_ROOT + eq.getDetail())
                .color(eq.getIntensity().getColor())
                .timestamp(eq.getTime().atZone(TimeProvider.ZONE_ID).toInstant())
                .build();
    }

    private void addIntensityGroupFields(I18nEmbedCreateSpec.Builder builder, Earthquake eq) {
        eq.getRelative().getGroups().forEach(group -> builder.addField("eewbot.quakeinfo.field.intensity",
                group.getAreas().stream().map(Earthquake.Relative.Group.Area::getName).collect(Collectors.joining(" ")),
                false, group.getIntensity()));
    }
}
