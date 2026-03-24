package net.teamfruit.eewbot.registry.destination;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.legacy.ChannelRegistryJson;
import net.teamfruit.eewbot.registry.destination.migration.ChannelMigration;
import net.teamfruit.eewbot.registry.destination.model.*;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence test for JSON to PostgreSQL migration.
 * Uses real production channels.json data (old format with isGuild, webhook {id, token})
 * to verify that ChannelRegistryJson and ChannelRegistrySql (PostgreSQL) produce identical results
 * after migration via ChannelMigration.
 *
 * <p>Requires Docker to be available. Tests are skipped (not failed) when Docker is unavailable.
 */
@org.junit.jupiter.api.Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class JsonToPostgresMigrationEquivalenceTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static ChannelRegistrySql sqlRegistry;

    @TempDir
    Path tempDir;

    private ChannelRegistryJson jsonRegistry;

    /**
     * Channels loaded from resource file, used for dynamic assertions.
     */
    private Map<Long, Channel> testChannels;

    @BeforeAll
    static void initPostgres() {
        ConfigV2.PostgreSQL pgConfig = new ConfigV2.PostgreSQL();
        pgConfig.setHost(POSTGRES.getHost());
        pgConfig.setPort(POSTGRES.getFirstMappedPort());
        pgConfig.setDatabase(POSTGRES.getDatabaseName());
        pgConfig.setUsername(POSTGRES.getUsername());
        pgConfig.setPassword(POSTGRES.getPassword());

        sqlRegistry = ChannelRegistrySql.forPostgreSQL(pgConfig);
        DatabaseInitializer.migrate(sqlRegistry.getDataSource(), SQLDialect.POSTGRES);
    }

    @AfterAll
    static void closePostgres() {
        if (sqlRegistry != null) {
            sqlRegistry.close();
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        // Truncate destinations table for a clean state each test
        sqlRegistry.getDsl().truncate("destinations").cascade().execute();

        // Setup Gson with full deserialization support (including old format migration)
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
                .registerTypeAdapter(Channel.class, new ChannelDeserializer())
                .registerTypeAdapter(ChannelWebhook.class, new ChannelWebhookDeserializer())
                .create();

        // Copy resource file to temp directory
        Path jsonPath = this.tempDir.resolve("channels.json");
        try (InputStream is = getClass().getResourceAsStream("/migration/channels_old_format.json")) {
            Files.copy(is, jsonPath);
        }

        // Load JSON registry (migrateOldFormat() sets channelId for old-format entries)
        this.jsonRegistry = new ChannelRegistryJson(jsonPath, gson);
        this.jsonRegistry.load(false);

        // Keep reference to loaded channels for assertions
        this.testChannels = this.jsonRegistry.getAllChannels();

        // Use actual migration logic to migrate data from JSON to SQL
        List<Map.Entry<Long, Channel>> entries = ChannelMigration.collectChannels(this.jsonRegistry);
        ChannelMigration.migrateChannelsSql(entries, sqlRegistry, sqlRegistry.getDsl());
    }

    // ===== get(long key) tests =====

    @Test
    @DisplayName("get() should return equal Channel for all test channels")
    void testGet_allChannels() {
        for (Long targetId : this.testChannels.keySet()) {
            Channel jsonChannel = this.jsonRegistry.get(targetId);
            Channel sqlChannel = sqlRegistry.get(targetId);

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
        Channel sqlChannel = sqlRegistry.get(nonExistentKey);

        assertThat(jsonChannel).isNull();
        assertThat(sqlChannel).isNull();
    }

    // ===== exists(long key) tests =====

    @Test
    @DisplayName("exists() should return true for existing keys")
    void testExists_existingKeys() {
        for (Long targetId : this.testChannels.keySet()) {
            boolean jsonExists = this.jsonRegistry.exists(targetId);
            boolean sqlExists = sqlRegistry.exists(targetId);

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
            boolean sqlExists = sqlRegistry.exists(key);

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
        List<Long> sqlResult = sqlRegistry.getWebhookAbsentChannels();

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
        List<Long> sqlResult = sqlRegistry.getWebhookAbsentChannels(filter);

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent guild channels")
                .isEqualTo(new HashSet<>(jsonResult));
    }

    @Test
    @DisplayName("getWebhookAbsentChannels(filter) with eewAlert=true should return equal results")
    void testGetWebhookAbsentChannels_eewAlertFilter() {
        ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();

        List<Long> jsonResult = this.jsonRegistry.getWebhookAbsentChannels(filter);
        List<Long> sqlResult = sqlRegistry.getWebhookAbsentChannels(filter);

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent eewAlert channels")
                .isEqualTo(new HashSet<>(jsonResult));
    }

    @Test
    @DisplayName("getWebhookAbsentChannels(filter) with intensity=ONE should return equal results")
    void testGetWebhookAbsentChannels_intensityFilter() {
        ChannelFilter filter = ChannelFilter.builder().intensity(SeismicIntensity.ONE).build();

        List<Long> jsonResult = this.jsonRegistry.getWebhookAbsentChannels(filter);
        List<Long> sqlResult = sqlRegistry.getWebhookAbsentChannels(filter);

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent channels with intensity<=ONE")
                .isEqualTo(new HashSet<>(jsonResult));
    }

    // ===== getAllChannels() tests =====

    @Test
    @DisplayName("getAllChannels() should return equal results")
    void testGetAllChannels() {
        Map<Long, Channel> jsonResult = this.jsonRegistry.getAllChannels();
        Map<Long, Channel> sqlResult = sqlRegistry.getAllChannels();

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
        int sqlRemoved = sqlRegistry.removeByGuildId(guildId);

        assertThat(sqlRemoved)
                .as("Removed count for guild %d", guildId)
                .isEqualTo(jsonRemoved)
                .isEqualTo((int) expectedCount);

        // Verify channels are removed
        for (Long targetId : guildTargetIds) {
            assertThat(this.jsonRegistry.exists(targetId)).as("json exists(%d)", targetId).isFalse();
            assertThat(sqlRegistry.exists(targetId)).as("sql exists(%d)", targetId).isFalse();
        }

        // Verify other channels are not affected
        for (Long targetId : nonGuildTargetIds) {
            assertThat(this.jsonRegistry.exists(targetId)).as("json exists(%d)", targetId).isTrue();
            assertThat(sqlRegistry.exists(targetId)).as("sql exists(%d)", targetId).isTrue();
        }
    }

    @Test
    @DisplayName("removeByGuildId() for non-existent guild should return 0")
    void testRemoveByGuildId_nonExistentGuild() {
        int jsonRemoved = this.jsonRegistry.removeByGuildId(99999L);
        int sqlRemoved = sqlRegistry.removeByGuildId(99999L);

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
        assertThat(sqlRegistry.get(targetId).getWebhook()).isNotNull();

        int jsonCleared = this.jsonRegistry.clearWebhookByUrls(List.of(webhookUrl));
        int sqlCleared = sqlRegistry.clearWebhookByUrls(List.of(webhookUrl));

        assertThat(sqlCleared)
                .as("Cleared count")
                .isEqualTo(jsonCleared)
                .isEqualTo(1);

        // Verify webhook is cleared
        assertThat(this.jsonRegistry.get(targetId).getWebhook()).isNull();
        assertThat(sqlRegistry.get(targetId).getWebhook()).isNull();

        // Verify other webhooks are not affected
        if (otherWithWebhook.isPresent()) {
            Long otherId = otherWithWebhook.get().getKey();
            assertThat(this.jsonRegistry.get(otherId).getWebhook()).isNotNull();
            assertThat(sqlRegistry.get(otherId).getWebhook()).isNotNull();
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
        int sqlCleared = sqlRegistry.clearWebhookByUrls(urls);

        assertThat(sqlCleared)
                .as("Cleared count for mixed URLs")
                .isEqualTo(jsonCleared)
                .isEqualTo(1);

        assertThat(this.jsonRegistry.get(targetId).getWebhook()).isNull();
        assertThat(sqlRegistry.get(targetId).getWebhook()).isNull();
    }

    @Test
    @DisplayName("clearWebhookByUrls() with empty list should return 0")
    void testClearWebhookByUrls_emptyList() {
        int jsonCleared = this.jsonRegistry.clearWebhookByUrls(List.of());
        int sqlCleared = sqlRegistry.clearWebhookByUrls(List.of());

        assertThat(sqlCleared)
                .as("Cleared count for empty list")
                .isEqualTo(jsonCleared)
                .isEqualTo(0);
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
        int sqlUpdated = sqlRegistry.setLangByGuildId(guildId, "ko_kr");

        assertThat(sqlUpdated)
                .as("Updated count for guild %d", guildId)
                .isEqualTo(jsonUpdated)
                .isEqualTo((int) expectedCount);

        // Verify language is updated for all guild channels
        for (Long targetId : guildTargetIds) {
            assertThat(this.jsonRegistry.get(targetId).getLang()).as("json lang(%d)", targetId).isEqualTo("ko_kr");
            assertThat(sqlRegistry.get(targetId).getLang()).as("sql lang(%d)", targetId).isEqualTo("ko_kr");
        }

        // Verify non-guild channels are not affected
        if (nonGuildEntry.isPresent()) {
            Long dmTargetId = nonGuildEntry.get().getKey();
            String originalLang = nonGuildEntry.get().getValue().getLang();
            assertThat(this.jsonRegistry.get(dmTargetId).getLang()).isEqualTo(originalLang);
            assertThat(sqlRegistry.get(dmTargetId).getLang()).isEqualTo(originalLang);
        }
    }

    @Test
    @DisplayName("setLangByGuildId() for non-existent guild should return 0")
    void testSetLangByGuildId_nonExistentGuild() {
        int jsonUpdated = this.jsonRegistry.setLangByGuildId(99999L, "ko_kr");
        int sqlUpdated = sqlRegistry.setLangByGuildId(99999L, "ko_kr");

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
        DeliveryPartition sqlResult = sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

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
        DeliveryPartition sqlResult = sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

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
        DeliveryPartition sqlResult = sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

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
        // Find a channel with a webhook
        Map.Entry<Long, Channel> withWebhook = this.testChannels.entrySet().stream()
                .filter(e -> e.getValue().getWebhook() != null)
                .findFirst()
                .orElseThrow();
        long webhookId = withWebhook.getValue().getWebhook().id();
        long targetId = withWebhook.getKey();

        boolean jsonResult = this.jsonRegistry.isWebhookExclusiveToTarget(webhookId, targetId);
        boolean sqlResult = sqlRegistry.isWebhookExclusiveToTarget(webhookId, targetId);

        assertThat(sqlResult)
                .as("isWebhookExclusiveToTarget(%d, %d)", webhookId, targetId)
                .isEqualTo(jsonResult)
                .isTrue();
    }

    @Test
    @DisplayName("isWebhookExclusiveToTarget() for webhook used by different destination")
    void testIsWebhookForThread_differentDestination() {
        // Find a channel with a webhook and another channel without
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
        boolean sqlResult = sqlRegistry.isWebhookExclusiveToTarget(webhookId, differentTargetId);

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
        boolean sqlResult = sqlRegistry.isWebhookExclusiveToTarget(99999L, anyTargetId);

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
        ChannelMigration.migrateChannelsSql(entries, sqlRegistry, sqlRegistry.getDsl());

        // Verify SQL has default lang instead of null
        Channel sqlChannel = sqlRegistry.get(9001L);
        assertThat(sqlChannel.getLang()).isEqualTo("ja_jp");
    }
}
