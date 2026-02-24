package net.teamfruit.eewbot.registry.destination.legacy;

import com.google.gson.Gson;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelObjectMapper;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.apache.commons.lang3.Strings;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.json.JsonSetParams;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;

import java.util.*;
import java.util.stream.Collectors;

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

    public void init() {
        Log.logger.info("Connecting to Redis");

        try {
            this.jedisPool.ftInfo("channel-index");
            // Migrate old format in existing Redis data
            migrateOldFormat();
        } catch (JedisDataException e) {
            throw new IllegalStateException("New Redis deployments are no longer supported. Please use 'sqlite' or 'postgresql' as database.type.");
        }
    }

    private void migrateOldFormat() {
        // Scan all channel entries and migrate old format
        List<String> keys = new ArrayList<>();
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder("*")
                .load("__key")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> keys.add(row.getString("__key")));
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);

        boolean migrated = false;
        for (String key : keys) {
            Channel channel = this.jedisPool.jsonGet(key, Channel.class);
            if (channel != null && channel.getChannelId() == null) {
                // Old format: set channelId to targetId
                String idPart = Strings.CS.removeStart(key, CHANNEL_PREFIX);
                try {
                    long targetId = Long.parseLong(idPart);
                    channel.setChannelId(targetId);
                    this.jedisPool.jsonSet(key, Path.ROOT_PATH, channel);
                    migrated = true;
                } catch (NumberFormatException e) {
                    Log.logger.warn("Skipping migration of channel with non-numeric key suffix: {}", key, e);
                }
            }
        }
        if (migrated) {
            Log.logger.info("Migrated old Redis channel format to destination model");
        }
    }

/*    private void createJedisIndex() {
        Schema schema = new Schema()
                .addNumericField("$.channelId").as("channelId")
                .addNumericField("$.threadId").as("threadId")
                .addNumericField("$.guildId").as("guildId")
                .addTagField("$.eewAlert").as("eewAlert")
                .addTagField("$.eewPrediction").as("eewPrediction")
                .addTagField("$.eewDecimation").as("eewDecimation")
                .addTagField("$.quakeInfo").as("quakeInfo")
                .addNumericField("$.minIntensity").as("minIntensity")
                .addNumericField("$.webhook.id").as("webhookId");
        IndexDefinition indexDefinition = new IndexDefinition(IndexDefinition.Type.JSON)
                .setPrefixes(CHANNEL_PREFIX);
        this.jedisPool.ftCreate(CHANNEL_INDEX, IndexOptions.defaultOptions().setDefinition(indexDefinition), schema);
    }*/

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
    public void putAll(Map<Long, Channel> channels) {
        if (channels.isEmpty()) {
            return;
        }
        try (Connection connection = this.jedisPool.getPool().getResource()) {
            Transaction transaction = new Transaction(connection);
            transaction.setJsonObjectMapper(this.objectMapper);
            channels.forEach((key, channel) ->
                    transaction.jsonSet(CHANNEL_PREFIX + key, Path.ROOT_PATH, channel, new JsonSetParams().nx())
            );
            transaction.exec();
        }
    }

    @Override
    public void put(long key, Channel channel) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.ROOT_PATH, channel, new JsonSetParams().nx());
    }

    @Override
    public void set(long key, String name, boolean bool) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$." + name), bool);
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.minIntensity"), intensity.getCode());
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.webhook"), webhook);
    }

    @Override
    public void setLang(long key, String lang) {
        this.jedisPool.jsonSet(CHANNEL_PREFIX + key, Path.of("$.lang"), lang);
    }

    /**
     * Parse a channel ID from a Redis key, handling malformed values safely.
     *
     * @param key the full Redis key (expected to start with CHANNEL_PREFIX)
     * @return the parsed channel ID, or {@code null} if the key is invalid
     */
    private Long parseChannelIdFromKey(final String key) {
        if (key == null || !key.startsWith(CHANNEL_PREFIX)) {
            Log.logger.warn("Unexpected key format (missing prefix) when parsing channel ID: {}", key);
            return null;
        }
        final String idPart = Strings.CS.removeStart(key, CHANNEL_PREFIX);
        try {
            return Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            Log.logger.warn("Failed to parse channel ID from key: {}", key, e);
            return null;
        }
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
            aggregationResult.getRows().forEach(row -> {
                Long channelId = parseChannelIdFromKey(row.getString("__key"));
                if (channelId != null) {
                    list.add(channelId);
                }
            });
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);
        return list;
    }

    @Override
    public List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
        String base = filter != null ? filter.toQueryString() : "";
        String query = base.isEmpty() ? "-@webhookId:[0 inf]" : base + " -@webhookId:[0 inf]";

        List<Long> list = new ArrayList<>();
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder(query)
                .load("__key")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> {
                Long channelId = parseChannelIdFromKey(row.getString("__key"));
                if (channelId != null) {
                    list.add(channelId);
                }
            });
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);
        return list;
    }

    @Override
    public int removeByGuildId(long guildId) {
        List<String> keysToRemove = new ArrayList<>();
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder("@guildId:[" + guildId + " " + guildId + "]")
                .load("__key")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> keysToRemove.add(row.getString("__key")));
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);

        if (!keysToRemove.isEmpty()) {
            try (Connection connection = this.jedisPool.getPool().getResource()) {
                Transaction transaction = new Transaction(connection);
                for (String key : keysToRemove) {
                    transaction.jsonDel(key);
                }
                transaction.exec();
            }
        }
        return keysToRemove.size();
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

        List<String> keysToUpdate = new ArrayList<>();
        for (long webhookId : webhookIds) {
            AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX,
                    new AggregationBuilder("@webhookId:[" + webhookId + " " + webhookId + "]")
                            .load("__key")
                            .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
            long cursorId;
            do {
                cursorId = aggregationResult.getCursorId();
                aggregationResult.getRows().forEach(row -> keysToUpdate.add(row.getString("__key")));
                if (cursorId != 0)
                    aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
            } while (cursorId != 0);
        }

        for (String key : keysToUpdate) {
            this.jedisPool.jsonSet(key, Path.of("$.webhook"), (Object) null);
        }
        return keysToUpdate.size();
    }

    @Override
    public int setLangByGuildId(long guildId, String lang) {
        List<String> keysToUpdate = new ArrayList<>();
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder("@guildId:[" + guildId + " " + guildId + "]")
                .load("__key")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> keysToUpdate.add(row.getString("__key")));
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);

        for (String key : keysToUpdate) {
            this.jedisPool.jsonSet(key, Path.of("$.lang"), lang);
        }
        return keysToUpdate.size();
    }

    @Override
    public Map<Long, Channel> getAllChannels() {
        Map<Long, Channel> result = new HashMap<>();
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder("*")
                .load("__key")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> {
                Long channelId = parseChannelIdFromKey(row.getString("__key"));
                if (channelId != null) {
                    Channel channel = this.jedisPool.jsonGet(row.getString("__key"), Channel.class);
                    if (channel != null) {
                        result.put(channelId, channel);
                    }
                }
            });
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);
        return result;
    }

    @Override
    public DeliveryPartition getDeliveryChannels(ChannelFilter filter) {
        Map<Long, DeliveryTarget> webhookPresent = new HashMap<>();
        Map<Long, DeliveryTarget> webhookAbsent = new HashMap<>();

        String query = filter != null ? filter.toQueryString() : "*";
        AggregationResult aggregationResult = this.jedisPool.ftAggregate(CHANNEL_INDEX, new AggregationBuilder(query)
                .load("__key", "$.webhook", "$.lang")
                .cursor(AGGREGATION_CURSOR_COUNT, AGGREGATION_CURSOR_TIMEOUT));
        long cursorId;
        do {
            cursorId = aggregationResult.getCursorId();
            aggregationResult.getRows().forEach(row -> {
                String key = row.getString("__key");
                long targetId;
                try {
                    targetId = Long.parseLong(Strings.CS.removeStart(key, CHANNEL_PREFIX));
                } catch (NumberFormatException e) {
                    Log.logger.warn("Invalid channel key in Redis index: {}", key, e);
                    return;
                }

                String lang = row.getString("$.lang");

                if (row.get("$.webhook") != null) {
                    ChannelWebhook webhook = EEWBot.GSON.fromJson(row.getString("$.webhook"), ChannelWebhook.class);
                    webhookPresent.put(targetId, new DeliveryTarget(targetId, lang, webhook.getUrl()));
                } else {
                    webhookAbsent.put(targetId, new DeliveryTarget(targetId, lang, null));
                }
            });
            if (cursorId != 0)
                aggregationResult = this.jedisPool.ftCursorRead(CHANNEL_INDEX, cursorId, AGGREGATION_CURSOR_COUNT);
        } while (cursorId != 0);
        return new DeliveryPartition(webhookPresent, webhookAbsent);
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long targetId) {
        // Check if this webhook is used by a different destination
        Query query = new Query("@webhookId:[" + webhookId + " " + webhookId + "]").setNoContent();
        SearchResult searchResult = this.jedisPool.ftSearch(CHANNEL_INDEX, query);

        // If webhook is used by any destination other than targetId, return false
        for (var doc : searchResult.getDocuments()) {
            String idWithoutPrefix = Strings.CS.removeStart(doc.getId(), CHANNEL_PREFIX);
            try {
                long docTargetId = Long.parseLong(idWithoutPrefix);
                if (docTargetId != targetId) {
                    return false;
                }
            } catch (NumberFormatException e) {
                // Skip documents with invalid numeric IDs to avoid unexpected runtime exceptions
                Log.logger.warn("Invalid channel id in Redis document id: {}", doc.getId(), e);
            }
        }
        return true;
    }

    public void close() {
        this.jedisPool.close();
    }
}
