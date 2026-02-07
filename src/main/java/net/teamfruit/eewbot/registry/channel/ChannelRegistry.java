package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
public interface ChannelRegistry {

    Channel get(long key);

    void remove(long key);

    boolean exists(long key);

    void put(long key, Channel channel);

    void set(long key, String name, boolean bool);

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
     * @param lang the language code
     * @return the number of channels updated
     */
    int setLangByGuildId(long guildId, String lang);

    /**
     * Get all channels as a map.
     *
     * @return a map of target ID to Channel
     */
    Map<Long, Channel> getAllChannels();

    Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter);

    boolean isWebhookForThread(long webhookId, long threadId);

    default void putAllIfAbsent(Map<Long, Channel> channels) {
        channels.forEach(this::put);
    }

    default void save() throws IOException {
    }

}
