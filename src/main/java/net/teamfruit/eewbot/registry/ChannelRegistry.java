package net.teamfruit.eewbot.registry;

import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.IndexDefinition;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Schema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelRegistry extends ConfigurationRegistry<ConcurrentMap<Long, Channel>> {

    private static final String CHANNEL_PREFIX = "channel:";

    private UnifiedJedis unifiedJedis;
    private boolean redisReady = false;

    public ChannelRegistry(Path path) {
        super(path, ConcurrentHashMap::new, new TypeToken<ConcurrentHashMap<Long, Channel>>() {
        }.getType());
    }

    public void init(UnifiedJedis jedisPooled) throws IOException {
        this.unifiedJedis = jedisPooled;
        initJedis();
    }

    private void initJedis() throws IOException {
        Log.logger.info("Connecting to Redis");
        try {
            this.unifiedJedis.ftInfo("channel-index");
        } catch (JedisDataException e) {
            Log.logger.info("Creating index");
            createJedisIndex();
            if (Files.exists(getPath())) {
                Log.logger.info("Migrating to Redis");
                load();
                migrationToJedis();
                setElement(null);
                Log.logger.info("Migrated to Redis");
            }
        }
        this.redisReady = true;
    }

    private void createJedisIndex() {
        Schema schema = new Schema()
                .addTextField("eewAlert", 1.0)
                .addTextField("eewPrediction", 1.0)
                .addTextField("eewDecimation", 1.0)
                .addTextField("quakeInfo", 1.0)
                .addTextField("minIntensity", 1.0)
                .addNumericField("webhook.id")
                .addNumericField("webhook.threadId");
        IndexDefinition indexDefinition = new IndexDefinition()
                .setPrefixes(CHANNEL_PREFIX);
        this.unifiedJedis.ftCreate("channel-index", IndexOptions.defaultOptions().setDefinition(indexDefinition), schema);
    }

    public void migrationToJedis() {
        AbstractTransaction transaction = this.unifiedJedis.multi();
        getElement().forEach((key, channel) -> transaction.jsonSet(CHANNEL_PREFIX + key, EEWBot.GSON.toJson(channel)));
        transaction.exec();
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

    @Override
    public void save() throws IOException {
    }
}
