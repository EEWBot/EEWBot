package net.teamfruit.eewbot.registry;

import com.google.gson.Gson;
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

public class ChannelRegistry extends JsonRegistry<ConcurrentMap<Long, Channel>> {

    private static final String CHANNEL_PREFIX = "channel:";
    private static final String CHANNEL_INDEX = "channel-index";

    private JedisPooled jedisPool;
    private boolean redisReady = false;
    private final ChannelObjectMapper objectMapper;

    public ChannelRegistry(java.nio.file.Path path, Gson gson) {
        super(path, ConcurrentHashMap::new, new TypeToken<ConcurrentHashMap<Long, Channel>>() {
        }.getType(), gson);
        this.objectMapper = new ChannelObjectMapper(gson);
    }

    public void init(JedisPooled jedisPooled) throws IOException {
        this.jedisPool = jedisPooled;
        initJedis();
    }

    private void initJedis() throws IOException {
        Log.logger.info("Connecting to Redis");
        this.jedisPool.setJsonObjectMapper(this.objectMapper);
        try {
            this.jedisPool.ftInfo("channel-index");
        } catch (JedisDataException e) {
            Log.logger.info("Creating redis index");
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
                .addTagField("$.isGuild").as("isGuild")
                .addNumericField("$.guildId").as("guildId")
                .addTagField("$.eewAlert").as("eewAlert")
                .addTagField("$.eewPrediction").as("eewPrediction")
                .addTagField("$.eewDecimation").as("eewDecimation")
                .addTagField("$.quakeInfo").as("quakeInfo")
                .addNumericField("$.minIntensity").as("minIntensity")
                .addNumericField("$.webhook.id").as("webhookId")
                .addNumericField("$.webhook.threadId").as("webhookThreadId");
        IndexDefinition indexDefinition = new IndexDefinition(IndexDefinition.Type.JSON)
                .setPrefixes(CHANNEL_PREFIX);
        this.jedisPool.ftCreate(CHANNEL_INDEX, IndexOptions.defaultOptions().setDefinition(indexDefinition), schema);
    }

    private void migrationToJedis() {
        try (Connection connection = this.jedisPool.getPool().getResource()) {
            Transaction transaction = new Transaction(connection);
            transaction.setJsonObjectMapper(this.objectMapper);
            getElement().forEach((key, channel) -> transaction.jsonSet(CHANNEL_PREFIX + key, Path.ROOT_PATH, channel));
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

    public boolean exists(long key) {
        if (this.redisReady)
            return this.jedisPool.exists(CHANNEL_PREFIX + key);
        return getElement().containsKey(key);
    }

    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.ROOT_PATH, mappingFunction.apply(key), new JsonSetParams().nx());
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
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.minIntensity"), intensity.ordinal());
        else
            getElement().get(key).setMinIntensity(intensity);
    }

    public void setIsGuild(long key, boolean guild) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.isGuild"), guild);
        else
            getElement().get(key).setGuild(guild);
    }

    public void setWebhook(long key, Webhook webhook) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.webhook"), webhook);
        else
            getElement().get(key).setWebhook(webhook);
    }

    public void setLang(long key, String lang) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.lang"), lang);
        else
            getElement().get(key).setLang(lang);
    }

    public boolean isGuildEmpty() {
        if (this.redisReady) {
            Query query = new Query("-@isGuild:{true | false}").setNoContent();
            SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);
            return !searchResult.getDocuments().isEmpty();
        }
        return getElement().entrySet().stream().anyMatch(entry -> entry.getValue().isGuild() == null);
    }

    public void setGuildId(long channelId, long guildId) {
        if (this.redisReady)
            this.jedisPool.jsonSet(CHANNEL_PREFIX + channelId, Path.of("$.guildId"), guildId);
        else
            getElement().get(channelId).setGuildId(guildId);
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
        if (this.redisReady) {
            Query query = filter.toQuery().returnFields("$.isGuild", "$.webhook", "$.lang");
            SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);
            return searchResult.getDocuments().stream()
                    .collect(Collectors.partitioningBy(doc -> doc.hasProperty("$.webhook"),
                            Collectors.toMap(doc -> Long.parseLong(StringUtils.removeStart(doc.getId(), CHANNEL_PREFIX)), doc -> {
                                if (doc.hasProperty("$.webhook"))
                                    return new ChannelBase(EEWBot.GSON.fromJson(doc.getString("$.webhook"), Webhook.class), doc.getString("$.lang"));
                                return new ChannelBase(null, doc.getString("$.lang"));
                            })));
        }
        return getElement().entrySet().stream()
                .filter(entry -> filter.test(entry.getValue()))
                .collect(Collectors.partitioningBy(entry -> entry.getValue().getWebhook() != null, Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public boolean isWebhookForThread(long webhookId, long threadId) {
        if (this.redisReady) {
            Query query = new Query("@webhookId:[" + webhookId + " " + webhookId + "] -@webhookThreadId:[" + threadId + " " + threadId + "]").setNoContent();
            SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);
            return searchResult.getDocuments().isEmpty();
        }
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
    public ConcurrentMap<Long, Channel> getElement() {
        if (this.redisReady)
            throw new IllegalStateException("Redis is connected");
        return super.getElement();
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
