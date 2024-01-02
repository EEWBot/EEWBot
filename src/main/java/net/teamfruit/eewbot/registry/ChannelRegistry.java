package net.teamfruit.eewbot.registry;

import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import redis.clients.jedis.JedisPooled;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ChannelRegistry extends ConfigurationRegistry<ConcurrentMap<Long, Channel>> {

    private JedisPooled jedisPool;

    public ChannelRegistry(Path path) {
        super(path, ConcurrentHashMap::new, new TypeToken<ConcurrentHashMap<Long, Channel>>() {
        }.getType());
    }

    public void setJedis(JedisPooled jedisPooled) {
        this.jedisPool = jedisPooled;
    }

    public Channel get(long key) {
        return getElement().get(key);
    }

    public Channel remove(long key) {
        return getElement().remove(key);
    }

    public Channel computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        return getElement().computeIfAbsent(key, mappingFunction);
    }

    public void set(long key, String name, boolean bool) {
        getElement().get(key).set(name, bool);
    }

    public void setMinIntensity(long key, SeismicIntensity intensity) {
        getElement().get(key).setMinIntensity(intensity);
    }

    public void setWebhook(long key, Channel.Webhook webhook) {
        getElement().get(key).setWebhook(webhook);
    }

    public List<Map.Entry<Long, Channel>> getWebhookAbsentChannels() {
        return getElement().entrySet()
                .stream()
                .filter(entry -> entry.getValue().getWebhook() == null)
                .collect(Collectors.toList());
    }

    public Map<Long, Channel> getChannels(Predicate<Channel> filter) {
        return getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<Boolean, List<Map.Entry<Long, Channel>>> getChannelsPartitionedByWebhookPresent(Predicate<Channel> filter) {
        return getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().getWebhook() != null));
    }

}
