package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.io.IOException;
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

    void setFlag(long key, String flag, boolean bool);

    void setMinIntensity(long key, SeismicIntensity intensity);

    void setIsGuild(long key, boolean guild);

    void setWebhook(long key, ChannelWebhook webhook);

    void setLang(long key, String lang);

    boolean isGuildEmpty();

    void setGuildId(long channelId, long guildId);

    List<Long> getWebhookAbsentChannels();

    void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer);

    Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter);

    boolean isWebhookForThread(long webhookId, long threadId);

    default void save() throws IOException {
    }

}
