package net.teamfruit.eewbot.registry.destination.legacy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.JsonRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class ChannelRegistryJson extends JsonRegistry<ConcurrentMap<Long, Channel>> implements DestinationDeliveryRegistry, DestinationAdminRegistry {

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
    public void put(long key, Channel channel) {
        getElement().putIfAbsent(key, channel);
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
    public int clearWebhookByUrls(Collection<String> webhookUrls) {
        if (webhookUrls.isEmpty()) {
            return 0;
        }
        Set<Long> webhookIds = webhookUrls.stream()
                .map(url -> {
                    try {
                        return new ChannelWebhook(url).id();
                    } catch (Exception e) {
                        Log.logger.warn("Failed to parse webhook URL, skipping: {}", ChannelWebhook.maskWebhookUrl(url), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (webhookIds.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Map.Entry<Long, Channel> entry : getElement().entrySet()) {
            ChannelWebhook webhook = entry.getValue().getWebhook();
            if (webhook != null && webhookIds.contains(webhook.id())) {
                entry.getValue().setWebhook(null);
                count++;
            }
        }
        if (count > 0) {
            try {
                save();
            } catch (IOException e) {
                Log.logger.error("Failed to save after clearing webhooks", e);
                throw new UncheckedIOException("Failed to persist webhook clearing", e);
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
    public DeliveryPartition getDeliveryChannels(ChannelFilter filter) {
        Map<Long, DeliveryTarget> webhook = new HashMap<>();
        Map<Long, DeliveryTarget> direct = new HashMap<>();

        getElement().forEach((targetId, channel) -> {
            if (filter != null && !filter.test(channel)) return;
            String webhookUrl = channel.getWebhookUrl();
            DeliveryTarget target = new DeliveryTarget(targetId, channel.getLang(), webhookUrl);
            if (webhookUrl != null) {
                webhook.put(targetId, target);
            } else {
                direct.put(targetId, target);
            }
        });

        return new DeliveryPartition(webhook, direct);
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
