package net.teamfruit.eewbot.registry.channel;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.lang.reflect.Type;

public class SeismicIntensitySerializer implements JsonSerializer<SeismicIntensity> {

    @Override
    public JsonElement serialize(SeismicIntensity seismicIntensity, Type type, JsonSerializationContext jsonSerializationContext) {
        return new JsonPrimitive(seismicIntensity.ordinal());
    }
}
