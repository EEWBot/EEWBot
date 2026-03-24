package net.teamfruit.eewbot.registry.destination;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.legacy.ChannelRegistryJson;
import net.teamfruit.eewbot.registry.destination.legacy.ChannelRegistryRedis;
import net.teamfruit.eewbot.registry.destination.migration.ChannelMigration;
import net.teamfruit.eewbot.registry.destination.model.*;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence test for Redis to SQLite migration.
 * Loads old-format channel data from a pre-generated Redis dump (dump.rdb),
 * migrates via {@link ChannelMigration} to SQLite, and compares results
 * against a JSON reference registry loaded from the same fixture data.
 *
 * <p>Requires Docker to be available. Tests are skipped (not failed) when Docker is unavailable.
 */
@org.junit.jupiter.api.Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class RedisToSqliteMigrationEquivalenceTest {

    static final String REDIS_IMAGE = "redis/redis-stack-server:7.4.0-v3";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379)
            .withClasspathResourceMapping("migration/dump.rdb", "/data/dump.rdb", BindMode.READ_ONLY);

    @TempDir
    Path tempDir;

    private ChannelRegistryJson jsonRegistry;
    private ChannelRegistrySql sqlRegistry;
    private JedisPooled jedis;
    private Gson gson;

    /**
     * Channels loaded from JSON reference, used for dynamic assertions.
     */
    private Map<Long, Channel> testChannels;

    @BeforeEach
    void setUp() throws IOException {
        // Setup Gson with full deserialization support (including old format migration)
        this.gson = new GsonBuilder()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
                .registerTypeAdapter(Channel.class, new ChannelDeserializer())
                .registerTypeAdapter(ChannelWebhook.class, new ChannelWebhookDeserializer())
                .create();

        // Load JSON registry as reference
        Path jsonPath = this.tempDir.resolve("channels.json");
        try (InputStream is = getClass().getResourceAsStream("/migration/channels_old_format.json")) {
            Files.copy(is, jsonPath);
        }
        this.jsonRegistry = new ChannelRegistryJson(jsonPath, this.gson);
        this.jsonRegistry.load(false);

        // Connect to Redis (do NOT call init() — same as ChannelMigration CLI)
        this.jedis = new JedisPooled(REDIS.getHost(), REDIS.getFirstMappedPort());
        ChannelRegistryRedis redisRegistry = new ChannelRegistryRedis(this.jedis, this.gson);

        // Pre-verification: RediSearch index is restored from dump.rdb
        this.jedis.ftInfo("channel-index");

        // Pre-verification: all 8 channels readable from Redis
        Map<Long, Channel> redisChannels = redisRegistry.getAllChannels();
        assertThat(redisChannels).hasSize(8);

        // Create SQLite registry
        Path dbPath = this.tempDir.resolve("test.db");
        this.sqlRegistry = ChannelRegistrySql.forSQLite(dbPath);
        DatabaseInitializer.migrate(this.sqlRegistry.getDataSource(), SQLDialect.SQLITE);

        // Migrate Redis → SQLite via ChannelMigration
        List<Map.Entry<Long, Channel>> entries = ChannelMigration.collectChannels(redisRegistry);
        ChannelMigration.migrateChannelsSql(entries, this.sqlRegistry, this.sqlRegistry.getDsl());

        this.testChannels = this.jsonRegistry.getAllChannels();
    }

    @AfterEach
    void tearDown() {
        if (this.jedis != null) {
            this.jedis.close();
        }
        if (this.sqlRegistry != null) {
            this.sqlRegistry.close();
        }
    }

    // ===== Redis data integrity =====

    @Test
    @DisplayName("Redis getAllChannels() should match JSON reference (excluding channelId)")
    void testRedisAllChannelsMatchJsonReference() {
        try (JedisPooled jedis = new JedisPooled(REDIS.getHost(), REDIS.getFirstMappedPort())) {
            ChannelRegistryRedis redisRegistry = new ChannelRegistryRedis(jedis, this.gson);

            Map<Long, Channel> redisChannels = redisRegistry.getAllChannels();
            Map<Long, Channel> jsonChannels = this.jsonRegistry.getAllChannels();

            assertThat(redisChannels.keySet())
                    .as("Redis and JSON should have the same channel IDs")
                    .isEqualTo(jsonChannels.keySet());

            // Redis old-format has channelId=null (init() not called), while JSON's migrateOldFormat
            // sets channelId=targetId. Compare all fields except channelId.
            for (Long targetId : redisChannels.keySet()) {
                Channel redis = redisChannels.get(targetId);
                Channel json = jsonChannels.get(targetId);
                assertThat(redis.getGuildId()).as("guildId for %d", targetId).isEqualTo(json.getGuildId());
                assertThat(redis.getThreadId()).as("threadId for %d", targetId).isEqualTo(json.getThreadId());
                assertThat(redis.isEewAlert()).as("eewAlert for %d", targetId).isEqualTo(json.isEewAlert());
                assertThat(redis.isEewPrediction()).as("eewPrediction for %d", targetId).isEqualTo(json.isEewPrediction());
                assertThat(redis.isEewDecimation()).as("eewDecimation for %d", targetId).isEqualTo(json.isEewDecimation());
                assertThat(redis.isQuakeInfo()).as("quakeInfo for %d", targetId).isEqualTo(json.isQuakeInfo());
                assertThat(redis.getMinIntensity()).as("minIntensity for %d", targetId).isEqualTo(json.getMinIntensity());
                assertThat(redis.getWebhook()).as("webhook for %d", targetId).isEqualTo(json.getWebhook());
                assertThat(redis.getLang()).as("lang for %d", targetId).isEqualTo(json.getLang());
                // channelId intentionally excluded: Redis old-format has null, JSON has targetId
                assertThat(redis.getChannelId()).as("Redis channelId for %d should be null (old format)", targetId).isNull();
            }
        }
    }

    // ===== get(long key) tests =====

    @Test
    @DisplayName("get() should return equal Channel for all test channels")
    void testGet_allChannels() {
        for (Long targetId : this.testChannels.keySet()) {
            Channel jsonChannel = this.jsonRegistry.get(targetId);
            Channel sqlChannel = this.sqlRegistry.get(targetId);

            assertThat(sqlChannel)
                    .as("Channel for targetId=%d", targetId)
                    .isEqualTo(jsonChannel);
        }
    }

    @Test
    @DisplayName("get() should return null for non-existent key")
    void testGet_nonExistentKey() {
        long nonExistentKey = 99999L;

        Channel jsonChannel = this.jsonRegistry.get(nonExistentKey);
        Channel sqlChannel = this.sqlRegistry.get(nonExistentKey);

        assertThat(jsonChannel).isNull();
        assertThat(sqlChannel).isNull();
    }

    // ===== exists(long key) tests =====

    @Test
    @DisplayName("exists() should return true for existing keys")
    void testExists_existingKeys() {
        for (Long targetId : this.testChannels.keySet()) {
            boolean jsonExists = this.jsonRegistry.exists(targetId);
            boolean sqlExists = this.sqlRegistry.exists(targetId);

            assertThat(sqlExists)
                    .as("exists() for targetId=%d", targetId)
                    .isEqualTo(jsonExists)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("exists() should return false for non-existent keys")
    void testExists_nonExistentKeys() {
        long[] nonExistentKeys = {99999L, 0L, -1L};

        for (long key : nonExistentKeys) {
            boolean jsonExists = this.jsonRegistry.exists(key);
            boolean sqlExists = this.sqlRegistry.exists(key);

            assertThat(sqlExists)
                    .as("exists() for key=%d", key)
                    .isEqualTo(jsonExists)
                    .isFalse();
        }
    }

    // ===== getWebhookAbsentChannels() tests =====

    @Test
    @DisplayName("getWebhookAbsentChannels() should return equal results")
    void testGetWebhookAbsentChannels() {
        List<Long> jsonResult = this.jsonRegistry.getWebhookAbsentChannels();
        List<Long> sqlResult = this.sqlRegistry.getWebhookAbsentChannels();

        Set<Long> expectedAbsent = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getWebhook() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent channels")
                .isEqualTo(new HashSet<>(jsonResult))
                .isEqualTo(expectedAbsent);
    }

    // ===== getWebhookAbsentChannels(ChannelFilter) tests =====

    @Test
    @DisplayName("getWebhookAbsentChannels(filter) with hasGuild=true should return equal results")
    void testGetWebhookAbsentChannels_hasGuildFilter() {
        ChannelFilter filter = ChannelFilter.builder().hasGuild(true).build();

        List<Long> jsonResult = this.jsonRegistry.getWebhookAbsentChannels(filter);
        List<Long> sqlResult = this.sqlRegistry.getWebhookAbsentChannels(filter);

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent guild channels")
                .isEqualTo(new HashSet<>(jsonResult));
    }

    @Test
    @DisplayName("getWebhookAbsentChannels(filter) with eewAlert=true should return equal results")
    void testGetWebhookAbsentChannels_eewAlertFilter() {
        ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();

        List<Long> jsonResult = this.jsonRegistry.getWebhookAbsentChannels(filter);
        List<Long> sqlResult = this.sqlRegistry.getWebhookAbsentChannels(filter);

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent eewAlert channels")
                .isEqualTo(new HashSet<>(jsonResult));
    }

    @Test
    @DisplayName("getWebhookAbsentChannels(filter) with intensity=ONE should return equal results")
    void testGetWebhookAbsentChannels_intensityFilter() {
        ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.ONE).build();

        List<Long> jsonResult = this.jsonRegistry.getWebhookAbsentChannels(filter);
        List<Long> sqlResult = this.sqlRegistry.getWebhookAbsentChannels(filter);

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent channels with intensity<=ONE")
                .isEqualTo(new HashSet<>(jsonResult));
    }

    // ===== getAllChannels() tests =====

    @Test
    @DisplayName("getAllChannels() should return equal results")
    void testGetAllChannels() {
        Map<Long, Channel> jsonResult = this.jsonRegistry.getAllChannels();
        Map<Long, Channel> sqlResult = this.sqlRegistry.getAllChannels();

        assertThat(sqlResult.keySet())
                .as("All channel IDs")
                .isEqualTo(jsonResult.keySet())
                .hasSize(this.testChannels.size());

        for (Long targetId : sqlResult.keySet()) {
            assertThat(sqlResult.get(targetId))
                    .as("Channel for targetId=%d", targetId)
                    .isEqualTo(jsonResult.get(targetId));
        }
    }

    // ===== removeByGuildId() tests =====

    @Test
    @DisplayName("removeByGuildId() should remove channels belonging to the guild")
    void testRemoveByGuildId() {
        Long guildId = this.testChannels.values().stream()
                .map(Channel::getGuildId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();

        long expectedCount = this.testChannels.values().stream()
                .filter(c -> guildId.equals(c.getGuildId()))
                .count();

        Set<Long> guildTargetIds = this.testChannels.entrySet().stream()
                .filter(e -> guildId.equals(e.getValue().getGuildId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Set<Long> nonGuildTargetIds = this.testChannels.entrySet().stream()
                .filter(e -> !guildId.equals(e.getValue().getGuildId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        int jsonRemoved = this.jsonRegistry.removeByGuildId(guildId);
        int sqlRemoved = this.sqlRegistry.removeByGuildId(guildId);

        assertThat(sqlRemoved)
                .as("Removed count for guild %d", guildId)
                .isEqualTo(jsonRemoved)
                .isEqualTo((int) expectedCount);

        // Verify channels are removed
        for (Long targetId : guildTargetIds) {
            assertThat(this.jsonRegistry.exists(targetId)).as("json exists(%d)", targetId).isFalse();
            assertThat(this.sqlRegistry.exists(targetId)).as("sql exists(%d)", targetId).isFalse();
        }

        // Verify other channels are not affected
        for (Long targetId : nonGuildTargetIds) {
            assertThat(this.jsonRegistry.exists(targetId)).as("json exists(%d)", targetId).isTrue();
            assertThat(this.sqlRegistry.exists(targetId)).as("sql exists(%d)", targetId).isTrue();
        }
    }

    @Test
    @DisplayName("removeByGuildId() for non-existent guild should return 0")
    void testRemoveByGuildId_nonExistentGuild() {
        int jsonRemoved = this.jsonRegistry.removeByGuildId(99999L);
        int sqlRemoved = this.sqlRegistry.removeByGuildId(99999L);

        assertThat(sqlRemoved)
                .as("Removed count for non-existent guild")
                .isEqualTo(jsonRemoved)
                .isEqualTo(0);
    }

    // ===== clearWebhookByUrls() tests =====

    @Test
    @DisplayName("clearWebhookByUrls() should clear webhook from channels")
    void testClearWebhookByUrls() {
        Map.Entry<Long, Channel> withWebhook = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getWebhook() != null)
                .findFirst()
                .orElseThrow();
        Long targetId = withWebhook.getKey();
        String webhookUrl = withWebhook.getValue().getWebhookUrl();

        // Find another channel with webhook to verify it's not affected
        Optional<Map.Entry<Long, Channel>> otherWithWebhook = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getWebhook() != null && !e.getKey().equals(targetId))
                .findFirst();

        assertThat(this.jsonRegistry.get(targetId).getWebhook()).isNotNull();
        assertThat(this.sqlRegistry.get(targetId).getWebhook()).isNotNull();

        int jsonCleared = this.jsonRegistry.clearWebhookByUrls(List.of(webhookUrl));
        int sqlCleared = this.sqlRegistry.clearWebhookByUrls(List.of(webhookUrl));

        assertThat(sqlCleared)
                .as("Cleared count")
                .isEqualTo(jsonCleared)
                .isEqualTo(1);

        // Verify webhook is cleared
        assertThat(this.jsonRegistry.get(targetId).getWebhook()).isNull();
        assertThat(this.sqlRegistry.get(targetId).getWebhook()).isNull();

        // Verify other webhooks are not affected
        if (otherWithWebhook.isPresent()) {
            Long otherId = otherWithWebhook.get().getKey();
            assertThat(this.jsonRegistry.get(otherId).getWebhook()).isNotNull();
            assertThat(this.sqlRegistry.get(otherId).getWebhook()).isNotNull();
        }
    }

    @Test
    @DisplayName("clearWebhookByUrls() with mix of existing and non-existent URLs should clear only existing")
    void testClearWebhookByUrls_withNonExistentUrl() {
        Map.Entry<Long, Channel> withWebhook = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getWebhook() != null)
                .findFirst()
                .orElseThrow();
        Long targetId = withWebhook.getKey();
        String webhookUrl = withWebhook.getValue().getWebhookUrl();

        List<String> urls = List.of(
                webhookUrl,
                "https://discord.com/api/webhooks/99999/nonexistent_token"
        );
        int jsonCleared = this.jsonRegistry.clearWebhookByUrls(urls);
        int sqlCleared = this.sqlRegistry.clearWebhookByUrls(urls);

        assertThat(sqlCleared)
                .as("Cleared count for mixed URLs")
                .isEqualTo(jsonCleared)
                .isEqualTo(1);

        assertThat(this.jsonRegistry.get(targetId).getWebhook()).isNull();
        assertThat(this.sqlRegistry.get(targetId).getWebhook()).isNull();
    }

    @Test
    @DisplayName("clearWebhookByUrls() with empty list should return 0")
    void testClearWebhookByUrls_emptyList() {
        int jsonCleared = this.jsonRegistry.clearWebhookByUrls(List.of());
        int sqlCleared = this.sqlRegistry.clearWebhookByUrls(List.of());

        assertThat(sqlCleared)
                .as("Cleared count for empty list")
                .isEqualTo(jsonCleared)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("clearWebhookByUrls() in Redis with all invalid URLs should return 0")
    void testRedisClearWebhookByUrls_allInvalidUrls() {
        ChannelRegistryRedis redisRegistry = new ChannelRegistryRedis(this.jedis, this.gson);

        int cleared = redisRegistry.clearWebhookByUrls(List.of(
                "not-a-url",
                "",
                "https://example.com/webhook/123/token"
        ));

        assertThat(cleared)
                .as("Redis cleared count for all invalid URLs")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("clearWebhookByUrls() in Redis with mixed valid and invalid URLs should clear only valid")
    void testRedisClearWebhookByUrls_mixedValidAndInvalidUrls() {
        ChannelRegistryRedis redisRegistry = new ChannelRegistryRedis(this.jedis, this.gson);

        long tempTargetId = 999999001L;
        long tempWebhookId = 880001L;
        Map<String, Object> legacyChannel = new HashMap<>();
        legacyChannel.put("isGuild", true);
        legacyChannel.put("guildId", 12345L);
        legacyChannel.put("eewAlert", true);
        legacyChannel.put("eewPrediction", false);
        legacyChannel.put("eewDecimation", false);
        legacyChannel.put("quakeInfo", false);
        legacyChannel.put("minIntensity", SeismicIntensity.ONE.getCode());
        legacyChannel.put("lang", "ja_jp");
        legacyChannel.put("webhook", Map.of(
                "id", tempWebhookId,
                "token", "temporary-token"
        ));
        this.jedis.jsonSet(
                "channel:" + tempTargetId,
                redis.clients.jedis.json.Path.ROOT_PATH,
                legacyChannel
        );

        try {
            int cleared = redisRegistry.clearWebhookByUrls(List.of(
                    "not-a-url",
                    "https://discord.com/api/webhooks/" + tempWebhookId + "/temporary-token"
            ));

            assertThat(cleared)
                    .as("Redis cleared count for mixed valid/invalid URLs")
                    .isEqualTo(1);
            assertThat(redisRegistry.get(tempTargetId).getWebhook())
                    .as("Temporary channel webhook should be cleared")
                    .isNull();
        } finally {
            redisRegistry.remove(tempTargetId);
        }
    }

    // ===== setLangByGuildId() tests =====

    @Test
    @DisplayName("setLangByGuildId() should update language for all channels in guild")
    void testSetLangByGuildId() {
        Long guildId = this.testChannels.values().stream()
                .map(Channel::getGuildId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();

        long expectedCount = this.testChannels.values().stream()
                .filter(c -> guildId.equals(c.getGuildId()))
                .count();

        Set<Long> guildTargetIds = this.testChannels.entrySet().stream()
                .filter(e -> guildId.equals(e.getValue().getGuildId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Find a non-guild channel to verify it's not affected
        Optional<Map.Entry<Long, Channel>> nonGuildEntry = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getGuildId() == null)
                .findFirst();

        int jsonUpdated = this.jsonRegistry.setLangByGuildId(guildId, "ko_kr");
        int sqlUpdated = this.sqlRegistry.setLangByGuildId(guildId, "ko_kr");

        assertThat(sqlUpdated)
                .as("Updated count for guild %d", guildId)
                .isEqualTo(jsonUpdated)
                .isEqualTo((int) expectedCount);

        // Verify language is updated for all guild channels
        for (Long targetId : guildTargetIds) {
            assertThat(this.jsonRegistry.get(targetId).getLang()).as("json lang(%d)", targetId).isEqualTo("ko_kr");
            assertThat(this.sqlRegistry.get(targetId).getLang()).as("sql lang(%d)", targetId).isEqualTo("ko_kr");
        }

        // Verify non-guild channels are not affected
        if (nonGuildEntry.isPresent()) {
            Long dmTargetId = nonGuildEntry.get().getKey();
            String originalLang = nonGuildEntry.get().getValue().getLang();
            assertThat(this.jsonRegistry.get(dmTargetId).getLang()).isEqualTo(originalLang);
            assertThat(this.sqlRegistry.get(dmTargetId).getLang()).isEqualTo(originalLang);
        }
    }

    @Test
    @DisplayName("setLangByGuildId() for non-existent guild should return 0")
    void testSetLangByGuildId_nonExistentGuild() {
        int jsonUpdated = this.jsonRegistry.setLangByGuildId(99999L, "ko_kr");
        int sqlUpdated = this.sqlRegistry.setLangByGuildId(99999L, "ko_kr");

        assertThat(sqlUpdated)
                .as("Updated count for non-existent guild")
                .isEqualTo(jsonUpdated)
                .isEqualTo(0);
    }

    // ===== getChannelsPartitionedByWebhookPresent(ChannelFilter) tests =====

    @Test
    @DisplayName("getDeliveryChannels() with empty filter")
    void testGetDeliveryChannels_emptyFilter() {
        ChannelFilter filter = ChannelFilter.builder().build();

        DeliveryPartition jsonResult = this.jsonRegistry.getDeliveryChannels(filter);
        DeliveryPartition sqlResult = this.sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

        // Compare key sets
        assertThat(sqlResult.webhook().keySet())
                .as("Channels with webhook")
                .isEqualTo(jsonResult.webhook().keySet());

        assertThat(sqlResult.direct().keySet())
                .as("Channels without webhook")
                .isEqualTo(jsonResult.direct().keySet());

        // Deep compare DeliveryTarget fields
        for (Long targetId : sqlResult.webhook().keySet()) {
            DeliveryTarget sqlTarget = sqlResult.webhook().get(targetId);
            DeliveryTarget jsonTarget = jsonResult.webhook().get(targetId);
            assertDeliveryTargetFieldsEqual(sqlTarget, jsonTarget, targetId, "webhook-present");
        }

        for (Long targetId : sqlResult.direct().keySet()) {
            DeliveryTarget sqlTarget = sqlResult.direct().get(targetId);
            DeliveryTarget jsonTarget = jsonResult.direct().get(targetId);
            assertDeliveryTargetFieldsEqual(sqlTarget, jsonTarget, targetId, "webhook-absent");
        }
    }

    @Test
    @DisplayName("getDeliveryChannels(null) in JSON should return all channels")
    void testGetDeliveryChannels_nullFilterJson() {
        DeliveryPartition jsonResult = this.jsonRegistry.getDeliveryChannels(null);

        Set<Long> allFromPartition = new HashSet<>(jsonResult.webhook().keySet());
        allFromPartition.addAll(jsonResult.direct().keySet());

        assertThat(allFromPartition)
                .as("JSON delivery partition keys with null filter")
                .isEqualTo(this.jsonRegistry.getAllChannels().keySet());
    }

    @Test
    @DisplayName("getDeliveryChannels(null) in Redis should return all channels")
    void testGetDeliveryChannels_nullFilterRedis() {
        ChannelRegistryRedis redisRegistry = new ChannelRegistryRedis(this.jedis, this.gson);
        DeliveryPartition redisResult = redisRegistry.getDeliveryChannels(null);

        Set<Long> allFromPartition = new HashSet<>(redisResult.webhook().keySet());
        allFromPartition.addAll(redisResult.direct().keySet());

        assertThat(allFromPartition)
                .as("Redis delivery partition keys with null filter")
                .isEqualTo(redisRegistry.getAllChannels().keySet());
    }

    private void assertDeliveryTargetFieldsEqual(DeliveryTarget sql, DeliveryTarget json, Long targetId, String context) {
        assertThat(sql.targetId())
                .as("targetId for %s channel %d", context, targetId)
                .isEqualTo(json.targetId());
        assertThat(sql.lang())
                .as("lang for %s channel %d", context, targetId)
                .isEqualTo(json.lang());
        assertThat(sql.webhookUrl())
                .as("webhookUrl for %s channel %d", context, targetId)
                .isEqualTo(json.webhookUrl());
    }

    @Test
    @DisplayName("getDeliveryChannels() with hasGuild filter")
    void testGetDeliveryChannels_hasGuildFilter() {
        ChannelFilter filter = ChannelFilter.builder().hasGuild(true).build();

        DeliveryPartition jsonResult = this.jsonRegistry.getDeliveryChannels(filter);
        DeliveryPartition sqlResult = this.sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

        assertThat(sqlResult.webhook().keySet())
                .as("Guild channels with webhook")
                .isEqualTo(jsonResult.webhook().keySet());

        assertThat(sqlResult.direct().keySet())
                .as("Guild channels without webhook")
                .isEqualTo(jsonResult.direct().keySet());
    }

    @Test
    @DisplayName("getDeliveryChannels() with eewAlert filter")
    void testGetDeliveryChannels_eewAlertFilter() {
        ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();

        DeliveryPartition jsonResult = this.jsonRegistry.getDeliveryChannels(filter);
        DeliveryPartition sqlResult = this.sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

        assertThat(sqlResult.webhook().keySet())
                .as("EewAlert channels with webhook")
                .isEqualTo(jsonResult.webhook().keySet());

        assertThat(sqlResult.direct().keySet())
                .as("EewAlert channels without webhook")
                .isEqualTo(jsonResult.direct().keySet());
    }

    // ===== isWebhookExclusiveToTarget(long webhookId, long targetId) tests =====

    @Test
    @DisplayName("isWebhookExclusiveToTarget() for webhook used by single destination")
    void testIsWebhookForThread_singleDestination() {
        Map.Entry<Long, Channel> withWebhook = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getWebhook() != null)
                .findFirst()
                .orElseThrow();
        long webhookId = withWebhook.getValue().getWebhook().id();
        long targetId = withWebhook.getKey();

        boolean jsonResult = this.jsonRegistry.isWebhookExclusiveToTarget(webhookId, targetId);
        boolean sqlResult = this.sqlRegistry.isWebhookExclusiveToTarget(webhookId, targetId);

        assertThat(sqlResult)
                .as("isWebhookExclusiveToTarget(%d, %d)", webhookId, targetId)
                .isEqualTo(jsonResult)
                .isTrue();
    }

    @Test
    @DisplayName("isWebhookExclusiveToTarget() for webhook used by different destination")
    void testIsWebhookForThread_differentDestination() {
        Map.Entry<Long, Channel> withWebhook = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getWebhook() != null)
                .findFirst()
                .orElseThrow();
        long webhookId = withWebhook.getValue().getWebhook().id();

        Long differentTargetId = this.testChannels.entrySet().stream()
                .filter(e -> !e.getKey().equals(withWebhook.getKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        boolean jsonResult = this.jsonRegistry.isWebhookExclusiveToTarget(webhookId, differentTargetId);
        boolean sqlResult = this.sqlRegistry.isWebhookExclusiveToTarget(webhookId, differentTargetId);

        assertThat(sqlResult)
                .as("isWebhookExclusiveToTarget(%d, %d)", webhookId, differentTargetId)
                .isEqualTo(jsonResult)
                .isFalse();
    }

    @Test
    @DisplayName("isWebhookExclusiveToTarget() for non-existent webhook")
    void testIsWebhookForThread_nonExistentWebhook() {
        Long anyTargetId = this.testChannels.keySet().iterator().next();

        boolean jsonResult = this.jsonRegistry.isWebhookExclusiveToTarget(99999L, anyTargetId);
        boolean sqlResult = this.sqlRegistry.isWebhookExclusiveToTarget(99999L, anyTargetId);

        assertThat(sqlResult)
                .as("isWebhookExclusiveToTarget(99999, %d)", anyTargetId)
                .isEqualTo(jsonResult)
                .isTrue(); // No conflict found
    }

    // ===== Null value handling tests =====

    @Test
    @DisplayName("Channel with null lang should be migrated with default lang")
    void testNullLangChannel() throws IOException {
        // Use Gson WITHOUT ChannelDeserializer to preserve null lang in JSON
        Gson rawGson = new GsonBuilder()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
                .create();

        Path jsonPath = this.tempDir.resolve("null_lang_test.json");
        ConcurrentHashMap<Long, Channel> data = new ConcurrentHashMap<>();
        data.put(9001L, new Channel(
                null, 9001L, null,
                false, false, false, false,
                false, SeismicIntensity.ONE,
                null,
                null
        ));
        Files.writeString(jsonPath, rawGson.toJson(data));

        ChannelRegistryJson nullLangJson = new ChannelRegistryJson(jsonPath, rawGson);
        nullLangJson.load(false);

        // Verify source has null lang
        assertThat(nullLangJson.get(9001L).getLang()).isNull();

        // Migrate with default lang
        List<Map.Entry<Long, Channel>> entries = ChannelMigration.collectChannels(nullLangJson);
        ChannelMigration.migrateChannelsSql(entries, this.sqlRegistry, this.sqlRegistry.getDsl());

        // Verify SQL has default lang instead of null
        Channel sqlChannel = this.sqlRegistry.get(9001L);
        assertThat(sqlChannel.getLang()).isEqualTo("ja_jp");
    }
}
