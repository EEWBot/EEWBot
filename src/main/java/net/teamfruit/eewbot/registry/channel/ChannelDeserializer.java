package net.teamfruit.eewbot.registry.channel;

import com.google.gson.*;
import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.lang.reflect.Type;

public class ChannelDeserializer implements JsonDeserializer<Channel> {

    @Override
    public Channel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        // Extract fields
        boolean isGuild = obj.has("isGuild") && obj.get("isGuild").getAsBoolean();
        Long guildId = obj.has("guildId") && !obj.get("guildId").isJsonNull() ? obj.get("guildId").getAsLong() : null;

        // Migration logic: detect old format and convert
        Long channelId = null;
        Long threadId = null;

        if (obj.has("channelId") && !obj.get("channelId").isJsonNull()) {
            // New format
            channelId = obj.get("channelId").getAsLong();
            if (obj.has("threadId") && !obj.get("threadId").isJsonNull()) {
                threadId = obj.get("threadId").getAsLong();
            }
        } else {
            // Old format: infer from webhook.threadId
            if (obj.has("webhook") && !obj.get("webhook").isJsonNull()) {
                JsonObject webhookObj = obj.getAsJsonObject("webhook");
                if (webhookObj.has("threadId") && !webhookObj.get("threadId").isJsonNull()) {
                    // This is a thread entry: channelId is unknown, use target_id as fallback
                    threadId = webhookObj.get("threadId").getAsLong();
                    // channelId will be set later based on target_id (unknown parent)
                }
            }
            // channelId remains null for now - will be set by migration code
        }

        boolean eewAlert = obj.has("eewAlert") && obj.get("eewAlert").getAsBoolean();
        boolean eewPrediction = obj.has("eewPrediction") && obj.get("eewPrediction").getAsBoolean();
        boolean eewDecimation = obj.has("eewDecimation") && obj.get("eewDecimation").getAsBoolean();
        boolean quakeInfo = obj.has("quakeInfo") && obj.get("quakeInfo").getAsBoolean();

        SeismicIntensity minIntensity = SeismicIntensity.ONE;
        if (obj.has("minIntensity") && !obj.get("minIntensity").isJsonNull()) {
            minIntensity = context.deserialize(obj.get("minIntensity"), SeismicIntensity.class);
        }

        ChannelWebhook webhook = null;
        if (obj.has("webhook") && !obj.get("webhook").isJsonNull()) {
            webhook = context.deserialize(obj.get("webhook"), ChannelWebhook.class);
        }

        String lang = obj.has("lang") && !obj.get("lang").isJsonNull() ? obj.get("lang").getAsString() : "ja";

        return new Channel(isGuild, guildId, channelId, threadId, eewAlert, eewPrediction, eewDecimation, quakeInfo, minIntensity, webhook, lang);
    }
}
