package net.teamfruit.eewbot.registry.destination;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface DestinationAdminRegistry {

    Channel get(long key);

    boolean exists(long key);

    void put(long key, Channel channel);

    void remove(long key);

    void set(long key, String name, boolean bool);

    default void setAll(long key, Map<String, Boolean> values) {
        values.forEach((name, bool) -> set(key, name, bool));
    }

    void setMinIntensity(long key, SeismicIntensity intensity);

    void setWebhook(long key, ChannelWebhook webhook);

    void setLang(long key, String lang);

    List<Long> getWebhookAbsentChannels();

    List<Long> getWebhookAbsentChannels(ChannelFilter filter);

    /**
     * Remove all channels belonging to the specified guild.
     *
     * @param guildId the guild ID
     * @return the number of channels removed
     */
    int removeByGuildId(long guildId);

    /**
     * Clear (delete) webhook configuration for all channels using any of the specified webhook URLs.
     * Uses webhook_id for matching, so URLs with different ?thread_id parameters but the same
     * webhook ID are treated as the same webhook.
     *
     * @param webhookUrls the webhook URLs (may include ?thread_id query parameter)
     * @return the number of webhooks cleared
     */
    int clearWebhookByUrls(Collection<String> webhookUrls);

    /**
     * Set language for all channels belonging to the specified guild.
     *
     * @param guildId the guild ID
     * @param lang    the language code
     * @return the number of channels updated
     */
    int setLangByGuildId(long guildId, String lang);

    /**
     * Get all channels as a map.
     *
     * @return a map of target ID to Channel
     */
    Map<Long, Channel> getAllChannels();

    /**
     * Check whether the webhook ID is exclusive to the specified destination target.
     * This applies to both parent channels and threads. Even threads under the same parent
     * intentionally use separate webhooks so Discord webhook rate limits are distributed
     * per destination rather than shared across related channels.
     *
     * @param webhookId the webhook ID to inspect
     * @param targetId  the destination target ID that is allowed to own the webhook
     * @return {@code true} if no other destination uses the webhook ID
     */
    boolean isWebhookExclusiveToTarget(long webhookId, long targetId);

    default void save() throws IOException {
    }

}
