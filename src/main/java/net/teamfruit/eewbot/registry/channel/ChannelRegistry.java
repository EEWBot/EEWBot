package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ChannelRegistry {

    Channel get(long key);

    void remove(long key);

    boolean exists(long key);

    void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction);

    void set(long key, String name, boolean bool);

    void setMinIntensity(long key, SeismicIntensity intensity);

    void setWebhook(long key, ChannelWebhook webhook);

    void setLang(long key, String lang);

    boolean hasChannelsWithoutGuildId();

    void setGuildId(long channelId, long guildId);

    List<Long> getWebhookAbsentChannels();

    default List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
        if (filter == null) {
            return getWebhookAbsentChannels();
        }
        List<Long> results = new ArrayList<>();
        actionOnChannels(filter, channelId -> {
            Channel channel = get(channelId);
            if (channel != null && channel.getWebhook() == null) {
                results.add(channelId);
            }
        });
        return results;
    }

    void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer);

    Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter);

    boolean isWebhookForThread(long webhookId, long threadId);

    default void save() throws IOException {
    }

}
