package net.teamfruit.eewbot.registry;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelRegistryJson extends JsonRegistry<ConcurrentMap<Long, Channel>> implements ChannelRegistry {

    public ChannelRegistryJson(Path path, Gson gson) {
        super(path, ConcurrentHashMap::new, new TypeToken<ConcurrentHashMap<Long, Channel>>() {
        }.getType(), gson);
    }

    @Override
    public Channel get(long key) {
        return getElement().get(key);
    }

    @Override
    public void remove(long key) {
        getElement().remove(key);
    }

    @Override
    public boolean exists(long key) {
        return getElement().containsKey(key);
    }

    @Override
    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        getElement().computeIfAbsent(key, mappingFunction);
    }

    @Override
    public void set(long key, String name, boolean bool) {
        getElement().get(key).set(name, bool);
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        getElement().get(key).setMinIntensity(intensity);
    }

    @Override
    public void setIsGuild(long key, boolean guild) {
        getElement().get(key).setGuild(guild);
    }

    @Override
    public void setWebhook(long key, Webhook webhook) {
        getElement().get(key).setWebhook(webhook);
    }

    @Override
    public void setLang(long key, String lang) {
        getElement().get(key).setLang(lang);
    }

    @Override
    public boolean isGuildEmpty() {
        return getElement().entrySet().stream().anyMatch(entry -> entry.getValue().isGuild() == null);
    }

    @Override
    public void setGuildId(long channelId, long guildId) {
        getElement().get(channelId).setGuildId(guildId);
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        return getElement().entrySet()
                .stream()
                .filter(entry -> entry.getValue().getWebhook() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer) {
        getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .forEach(consumer);
    }

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        return getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().getWebhook() != null, Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long threadId) {
        return getElement().entrySet().stream().noneMatch(entry -> {
            Webhook webhook = entry.getValue().getWebhook();
            if (webhook == null || webhook.getId() != webhookId)
                return false;
            if (webhook.getThreadId() == null)
                return true;
            return webhook.getThreadId() != threadId;
        });
    }

    @Override
    public void save() throws IOException {
        super.save();
    }
}
