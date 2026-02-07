package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface ChannelRegistry {

    Channel get(long key);

    void remove(long key);

    boolean exists(long key);

    void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction);

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
     * Clear (delete) webhook configuration for all channels using the specified webhook URL.
     * Uses base URL (without ?thread_id query parameter) for prefix matching,
     * so all destinations sharing the same webhook are cleared.
     *
     * @param webhookUrl the webhook URL (may include ?thread_id query parameter)
     * @return the number of webhooks cleared
     */
    int clearWebhookByUrl(String webhookUrl);

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

    default void save() throws IOException {
    }

}
