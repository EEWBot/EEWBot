package net.teamfruit.eewbot.registry.channel;

import com.google.gson.*;

import java.lang.reflect.Type;

public class ChannelWebhookDeserializer implements JsonDeserializer<ChannelWebhook> {

    @Override
    public ChannelWebhook deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        // New format: { "url": "https://discord.com/api/webhooks/..." }
        if (obj.has("url")) {
            return new ChannelWebhook(obj.get("url").getAsString());
        }

        // Old format: { "id": 123, "token": "xxx", "threadId": 456 }
        long id = obj.get("id").getAsLong();
        String token = obj.get("token").getAsString();
        Long threadId = obj.has("threadId") && !obj.get("threadId").isJsonNull()
                ? obj.get("threadId").getAsLong() : null;
        return ChannelWebhook.of(id, token, threadId);
    }
}
