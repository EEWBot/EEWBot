package net.teamfruit.eewbot.registry;

import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.json.JsonSetParams;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.search.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelRegistry extends ConfigurationRegistry<ConcurrentMap<Long, Channel>> {

    private static final String CHANNEL_PREFIX = "channel:";
    private static final String CHANNEL_INDEX = "channel-index";

    private JedisPooled jedisPool;
    private boolean redisReady = false;

    public ChannelRegistry(java.nio.file.Path path) {
        super(path, ConcurrentHashMap::new, new TypeToken<ConcurrentHashMap<Long, Channel>>() {
        }.getType());
    }

    public void init(JedisPooled jedisPooled) throws IOException {
        this.jedisPool = jedisPooled;
        initJedis();
    }

    private void initJedis() throws IOException {
        Log.logger.info("Connecting to Redis");
        try {
            this.jedisPool.ftInfo("channel-index");
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
                .addTagField("$.eewAlert").as("eewAlert")
                .addTagField("$.eewPrediction").as("eewPrediction")
                .addTagField("$.eewDecimation").as("eewDecimation")
                .addTagField("$.quakeInfo").as("quakeInfo")
                .addTagField("$.minIntensity").as("minIntensity")
                .addNumericField("$.webhook.id").as("webhookId")
                .addNumericField("$.webhook.threadId").as("webhookThreadId");
        IndexDefinition indexDefinition = new IndexDefinition(IndexDefinition.Type.JSON)
                .setPrefixes(CHANNEL_PREFIX);
        this.jedisPool.ftCreate(CHANNEL_INDEX, IndexOptions.defaultOptions().setDefinition(indexDefinition), schema);
    }

    public void migrationToJedis() {
        try (Connection connection = this.jedisPool.getPool().getResource()) {
            Transaction transaction = new Transaction(connection);
            getElement().forEach((key, channel) -> transaction.jsonSet(CHANNEL_PREFIX + key, EEWBot.GSON.toJson(channel)));
            transaction.exec();
        }
    }

    public Channel get(long key) {
        if (this.redisReady)
            return this.jedisPool.jsonGet(CHANNEL_PREFIX + key, Channel.class);
        return getElement().get(key);
    }

    public void remove(long key) {
        if (this.redisReady)
            this.jedisPool.jsonDel(CHANNEL_PREFIX + key);
        else
            getElement().remove(key);
    }

    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, EEWBot.GSON.toJson(mappingFunction.apply(key)), new JsonSetParams().nx());
        else
            getElement().computeIfAbsent(key, mappingFunction);
    }

    public void set(long key, String name, boolean bool) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$." + name), bool);
        else
            getElement().get(key).set(name, bool);
    }

    public void setMinIntensity(long key, SeismicIntensity intensity) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.minIntensity"), intensity.getSerializedName());
        else
            getElement().get(key).setMinIntensity(intensity);
    }

    public void setWebhook(long key, Webhook webhook) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.webhook"), EEWBot.GSON.toJson(webhook));
        else
            getElement().get(key).setWebhook(webhook);
    }

    public List<Long> getWebhookAbsentChannels() {
        if (this.redisReady) {
            Query query = new Query("-@webhookId:[0 inf]").setNoContent();
            SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);
            return searchResult.getDocuments().stream()
                    .map(doc -> StringUtils.removeStart(doc.getId(), CHANNEL_PREFIX))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } else
            return getElement().entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().getWebhook() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
    }

    public void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer) {
        if (this.redisReady) {
            Query query = filter.toQuery().setNoContent();
            SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);
            searchResult.getDocuments().stream()
                    .map(doc -> StringUtils.removeStart(doc.getId(), CHANNEL_PREFIX))
                    .map(Long::parseLong)
                    .forEach(consumer);
        } else
            getElement().entrySet().stream()
                    .filter(entry -> filter.test(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .forEach(consumer);
    }

    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        return getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().getWebhook() != null, Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Override
    public void load() throws IOException {
        if (!this.redisReady)
            super.load();
    }

    @Override
    public void save() throws IOException {
        if (!this.redisReady)
            super.save();
    }
}
