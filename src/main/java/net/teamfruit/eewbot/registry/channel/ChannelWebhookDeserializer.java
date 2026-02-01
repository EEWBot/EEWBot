package net.teamfruit.eewbot.registry.channel;

import com.google.gson.*;

import java.lang.reflect.Type;

public class ChannelWebhookDeserializer implements JsonDeserializer<ChannelWebhook> {

    @Override
    public ChannelWebhook deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        long id = obj.get("id").getAsLong();
        String token = obj.get("token").getAsString();

        // Note: threadId is ignored in the new format - it's stored in Channel.threadId instead
        // This deserializer only reads id and token

        return new ChannelWebhook(id, token);
    }
}
