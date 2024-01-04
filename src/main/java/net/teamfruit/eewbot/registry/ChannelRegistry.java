package net.teamfruit.eewbot.registry;

import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.IndexDefinition;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Schema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelRegistry extends ConfigurationRegistry<ConcurrentMap<Long, Channel>> {

    private JedisPooled jedisPool;

    public ChannelRegistry(Path path) {
        super(path, ConcurrentHashMap::new, new TypeToken<ConcurrentHashMap<Long, Channel>>() {
        }.getType());
    }

    public void setJedis(JedisPooled jedisPooled) {
        this.jedisPool = jedisPooled;
        initJedis();
    }

    private void initJedis() {
        Schema schema = new Schema()
                .addTextField("eewAlert", 1.0)
                .addTextField("eewPrediction", 1.0)
                .addTextField("eewDecimation", 1.0)
                .addTextField("quakeInfo", 1.0)
                .addTextField("minIntensity", 1.0)
                .addNumericField("webhook.id")
                .addNumericField("webhook.threadId");
        IndexDefinition indexDefinition = new IndexDefinition()
                .setPrefixes("channel:");
        this.jedisPool.ftCreate("channel-index", IndexOptions.defaultOptions().setDefinition(indexDefinition), schema);
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

    public void actionOnChannels(ChannelFilter filter, Consumer<Map.Entry<Long, Channel>> consumer) {
        getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .forEach(consumer);
    }

    public Map<Boolean, List<Map.Entry<Long, Channel>>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        return getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().getWebhook() != null));
    }

}
