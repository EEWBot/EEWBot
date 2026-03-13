package net.teamfruit.eewbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.*;

public final class Codecs {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
            .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
            .registerTypeAdapter(Channel.class, new ChannelDeserializer())
            .registerTypeAdapter(ChannelWebhook.class, new ChannelWebhookDeserializer())
            .create();
    public static final Gson GSON_PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static final ObjectMapper XML_MAPPER = XmlMapper.builder().addModule(new JavaTimeModule()).build();

    private Codecs() {
    }
}
