package net.teamfruit.eewbot.registry;

import com.google.gson.Gson;
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
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ChannelRegistryRedis implements ChannelRegistry {

    private static final String CHANNEL_PREFIX = "channel:";
    private static final String CHANNEL_INDEX = "channel-index";
    private static final int AGGREGATION_CURSOR_COUNT = 1000;
    private static final int AGGREGATION_CURSOR_TIMEOUT = 30000;

    private final JedisPooled jedisPool;
    private final ChannelObjectMapper objectMapper;

    public ChannelRegistryRedis(JedisPooled jedisPooled, Gson gson) {
        this.jedisPool = jedisPooled;
        this.objectMapper = new ChannelObjectMapper(gson);
        this.jedisPool.setJsonObjectMapper(this.objectMapper);
    }

    public void init(Supplier<ChannelRegistryJson> migrationFrom) throws IOException {
        Log.logger.info("Connecting to Redis");

        try {
            this.jedisPool.ftInfo("channel-index");
        } catch (JedisDataException e) {
            Log.logger.info("Creating redis index");
            createJedisIndex();

            ChannelRegistryJson registryMigrationFrom = migrationFrom.get();
            if (Files.exists(registryMigrationFrom.getPath())) {
                Log.logger.info("Migrating to Redis");
                registryMigrationFrom.load();
                try (Connection connection = this.jedisPool.getPool().getResource()) {
                    Transaction transaction = new Transaction(connection);
                    transaction.setJsonObjectMapper(this.objectMapper);
                    registryMigrationFrom.getElement().forEach((key, channel) -> transaction.jsonSet(CHANNEL_PREFIX + key, Path.ROOT_PATH, channel));
                    transaction.exec();
                }
                Log.logger.info("Migrated to Redis");
            }
        }
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

    @Override
    public Channel get(long key) {
        return this.jedisPool.jsonGet(CHANNEL_PREFIX + key, Channel.class);
    }

    @Override
    public void remove(long key) {
        this.jedisPool.jsonDel(CHANNEL_PREFIX + key);
    }

    @Override
    public boolean exists(long key) {
        return this.jedisPool.exists(CHANNEL_PREFIX + key);
    }

    @Override
    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.ROOT_PATH, mappingFunction.apply(key), new JsonSetParams().nx());
    }

    @Override
    public void set(long key, String name, boolean bool) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$." + name), bool);
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.minIntensity"), intensity.ordinal());
    }

    @Override
    public void setIsGuild(long key, boolean guild) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.isGuild"), guild);
    }

    @Override
    public void setWebhook(long key, Webhook webhook) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.webhook"), webhook);
    }

    @Override
    public void setLang(long key, String lang) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.lang"), lang);
    }

    @Override
    public boolean isGuildEmpty() {
        Query query = new Query("-@isGuild:{true | false}").setNoContent();
        SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);
        return !searchResult.getDocuments().isEmpty();
    }

    @Override
    public void setGuildId(long channelId, long guildId) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + channelId, Path.of("$.guildId"), guildId);
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        List<Long> list = new ArrayList<>();
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder("-@webhookId:[0 inf]")
                .load("__key")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> list.add(Long.parseLong(StringUtils.removeStart(row.getString("__key"), CHANNEL_PREFIX))));
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);
        return list;
    }

    @Override
    public void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer) {
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder(filter.toQueryString())
                .load("__key")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> consumer.accept(Long.parseLong(StringUtils.removeStart(row.getString("__key"), CHANNEL_PREFIX))));
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);
    }

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        Map<Boolean, Map<Long, ChannelBase>> map = new HashMap<>();
        Map<Long, ChannelBase> webhookPresent = new HashMap<>();
        Map<Long, ChannelBase> webhookAbsent = new HashMap<>();
        map.put(true, webhookPresent);
        map.put(false, webhookAbsent);

        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder(filter.toQueryString())
                .load("__key", "$.isGuild", "$.webhook", "$.lang")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> {
                long channelId = Long.parseLong(StringUtils.removeStart(row.getString("__key"), CHANNEL_PREFIX));
                if (row.containsKey("$.webhook"))
                    webhookPresent.put(channelId, new ChannelBase(EEWBot.GSON.fromJson(row.getString("$.webhook"), Webhook.class), row.getString("$.lang")));
                else
                    webhookAbsent.put(channelId, new ChannelBase(null, row.getString("$.lang")));
            });
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);
        return map;
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long threadId) {
        Query query = new Query("@webhookId:[" + webhookId + " " + webhookId + "] -@webhookThreadId:[" + threadId + " " + threadId + "]").setNoContent();
        SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);
        return searchResult.getDocuments().isEmpty();
    }

}
