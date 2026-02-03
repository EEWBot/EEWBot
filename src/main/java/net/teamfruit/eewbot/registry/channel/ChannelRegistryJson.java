package net.teamfruit.eewbot.registry.channel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.JsonRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelRegistryJson extends JsonRegistry<ConcurrentMap<Long, Channel>> implements ChannelRegistry {

    public ChannelRegistryJson(Path path, Gson gson) {
        super(path, ConcurrentHashMap::new, new TypeToken<ConcurrentHashMap<Long, Channel>>() {
        }.getType(), gson);
    }

    @Override
    public void load(boolean createIfAbsent) throws IOException {
        super.load(createIfAbsent);
        // Migrate old format: if channelId is null, use target_id as fallback
        migrateOldFormat();
    }

    private void migrateOldFormat() {
        boolean migrated = false;
        for (Map.Entry<Long, Channel> entry : getElement().entrySet()) {
            Long targetId = entry.getKey();
            Channel channel = entry.getValue();
            if (channel.getChannelId() == null) {
                // Old format detected: set channelId to targetId (parent unknown)
                channel.setChannelId(targetId);
                migrated = true;
            }
        }
        if (migrated) {
            Log.logger.info("Migrated old channel format to destination model");
        }
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
    public void setWebhook(long key, ChannelWebhook webhook) {
        getElement().get(key).setWebhook(webhook);
    }

    @Override
    public void setLang(long key, String lang) {
        getElement().get(key).setLang(lang);
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
    public List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
        return getElement().entrySet()
                .stream()
                .filter(entry -> filter == null || filter.test(entry.getValue()))
                .filter(entry -> entry.getValue().getWebhook() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public int removeByGuildId(long guildId) {
        int count = 0;
        Iterator<Map.Entry<Long, Channel>> iterator = getElement().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Channel> entry = iterator.next();
            Long channelGuildId = entry.getValue().getGuildId();
            if (channelGuildId != null && channelGuildId == guildId) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }

    @Override
    public int clearWebhookByBaseUrl(String webhookUrl) {
        // Remove ?thread_id= query parameter to get base URL
        int queryIndex = webhookUrl.indexOf('?');
        String baseUrl = queryIndex >= 0 ? webhookUrl.substring(0, queryIndex) : webhookUrl;

        int count = 0;
        for (Map.Entry<Long, Channel> entry : getElement().entrySet()) {
            ChannelWebhook webhook = entry.getValue().getWebhook();
            if (webhook != null && webhook.getUrl().startsWith(baseUrl)) {
                entry.getValue().setWebhook(null);
                count++;
            }
        }
        return count;
    }

    @Override
    public int setLangByGuildId(long guildId, String lang) {
        int count = 0;
        for (Map.Entry<Long, Channel> entry : getElement().entrySet()) {
            Long channelGuildId = entry.getValue().getGuildId();
            if (channelGuildId != null && channelGuildId == guildId) {
                entry.getValue().setLang(lang);
                count++;
            }
        }
        return count;
    }

    @Override
    public Map<Long, Channel> getAllChannels() {
        return new HashMap<>(getElement());
    }

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        return getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().getWebhook() != null, Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long targetId) {
        // Check if this webhook is used by a different destination
        return getElement().entrySet().stream().noneMatch(entry -> {
            Long entryTargetId = entry.getKey();
            ChannelWebhook webhook = entry.getValue().getWebhook();
            if (webhook == null || webhook.id() != webhookId)
                return false;
            // If this is a different destination with the same webhook, return true (conflict)
            return !entryTargetId.equals(targetId);
        });
    }

    @Override
    public void save() throws IOException {
        super.save();
    }
}
