package net.teamfruit.eewbot.entity.other;

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
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.gateway.QuakeInfoGateway;
import net.teamfruit.eewbot.i18n.I18nDiscordEmbed;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@JacksonXmlRootElement(localName = "Root")
public class NHKDetailQuakeInfo implements Entity {

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
                "timestamp=" + this.timestamp +
                ", earthQuake=" + this.earthQuake +
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
                    "id='" + this.id + '\'' +
                    ", time=" + this.time +
                    ", intensity=" + this.intensity +
                    ", epicenter='" + this.epicenter + '\'' +
                    ", lat='" + this.lat + '\'' +
                    ", lon='" + this.lon + '\'' +
                    ", magnitude='" + this.magnitude + '\'' +
                    ", depth='" + this.depth + '\'' +
                    ", detail='" + this.detail + '\'' +
                    ", local='" + this.local + '\'' +
                    ", global='" + this.global + '\'' +
                    ", relative=" + this.relative +
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
                        "groups=" + this.groups +
                        '}';
            }

            public static class Group {

                @JacksonXmlProperty(localName = "Intensity", isAttribute = true)
                private SeismicIntensity intensity;

                @JacksonXmlProperty(localName = "Area")
                @JacksonXmlElementWrapper(useWrapping = false)
                private List<Area> areas;

                public SeismicIntensity getIntensity() {
                    return this.intensity;
                }

                public void setIntensity(final SeismicIntensity intensity) {
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
                            "intensity='" + this.intensity + '\'' +
                            ", areas=" + this.areas +
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
                                "name='" + this.name + '\'' +
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
            return LocalDateTime.parse(date, this.formatter);
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
            return SeismicIntensity.get(intensity);
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
        return MessageCreateSpec.builder().addEmbed(createD4JEmbed(lang)).build();
    }

    @Override
    public DiscordWebhook createWebhook(final String lang) {
        return DiscordWebhook.builder().addEmbed(createEmbed(lang, I18nDiscordEmbed.builder(lang))).build();
    }

    public EmbedCreateSpec createD4JEmbed(String lang) {
        return createEmbed(lang, I18nEmbedCreateSpec.builder(lang));
    }

    private <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        Earthquake eq = getEarthquake();
        Type type = getType();

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

    private <T> void addIntensityGroupFields(IEmbedBuilder<T> builder, Earthquake eq) {
        eq.getRelative().getGroups().forEach(group -> builder.addField("eewbot.quakeinfo.field.intensity",
                group.getAreas().stream().map(Earthquake.Relative.Group.Area::getName).collect(Collectors.joining(" ")),
                false, group.getIntensity().getSimple()));
    }
}
