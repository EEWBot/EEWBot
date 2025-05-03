package net.teamfruit.eewbot.registry.channel;

import com.google.gson.*;
import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.lang.reflect.Type;

public class SeismicIntensityDeserializer implements JsonDeserializer<SeismicIntensity> {

    @Override
    public SeismicIntensity deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                int ordinal = primitive.getAsInt();
                if (ordinal >= 0 && ordinal < SeismicIntensity.values().length) {
                    return SeismicIntensity.values()[ordinal];
                }
            } else if (primitive.isString()) {
                for (SeismicIntensity intensity : SeismicIntensity.values()) {
                    if (intensity.getLegacySerializedName().equals(primitive.getAsString())) {
                        return intensity;
                    }
                }
            }
        }
        throw new JsonParseException("Unexpected format for SeismicIntensity");
    }
}
