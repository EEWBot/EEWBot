package net.teamfruit.eewbot.registry.channel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence test for JSON to SQLite migration.
 * Verifies that ChannelRegistryJson and ChannelRegistrySql (SQLite) produce identical results
 * for all ChannelRegistry interface methods after migration via ChannelMigration.
 */
class JsonToSqliteMigrationEquivalenceTest {

    @TempDir
    Path tempDir;

    private ChannelRegistryJson jsonRegistry;
    private ChannelRegistrySql sqlRegistry;

    private Gson gson;

    // Test data: 8 channels with various configurations
    private static final Map<Long, Channel> TEST_CHANNELS = createTestChannels();

    private static Map<Long, Channel> createTestChannels() {
        Map<Long, Channel> channels = new LinkedHashMap<>();

        // 1: Basic, no webhook, minIntensity=ONE (boundary lower)
        channels.put(1001L, new Channel(
                100L, 1001L, null,
                false, false, false, false,
                SeismicIntensity.ONE,
                null,
                "ja_jp"
        ));

        // 2: Guild, with webhook, minIntensity=TWO (boundary upper)
        channels.put(1002L, new Channel(
                100L, 1002L, null,
                true, false, false, true,
                SeismicIntensity.TWO,
                ChannelWebhook.of(2001L, "token_2001"),
                "en_us"
        ));

        // 3: Thread (channelId != targetId), with webhook
        channels.put(1003L, new Channel(
                100L, 1000L, 1003L,
                false, true, false, false,
                SeismicIntensity.THREE,
                ChannelWebhook.of(2002L, "token_2002", 1003L),
                "ja_jp"
        ));

        // 4: DM (non-guild), guildId=null
        channels.put(1004L, new Channel(
                null, 1004L, null,
                true, true, false, true,
                SeismicIntensity.FOUR,
                null,
                "zh_tw"
        ));

        // 5: All flags ON, max intensity (SEVEN)
        channels.put(1005L, new Channel(
                200L, 1005L, null,
                true, true, true, true,
                SeismicIntensity.SEVEN,
                ChannelWebhook.of(2003L, "token_2003"),
                "ja_jp"
        ));

        // 6: lang=null, guildId=null (compound null)
        channels.put(1006L, new Channel(
                null, 1006L, null,
                false, false, true, false,
                SeismicIntensity.FIVE_MINUS,
                null,
                null
        ));

        // 7: UNKNOWN intensity (edge case)
        channels.put(1007L, new Channel(
                300L, 1007L, null,
                false, true, false, true,
                SeismicIntensity.UNKNOWN,
                ChannelWebhook.of(2004L, "token_2004"),
                "en_us"
        ));

        // 8: minIntensity=ONE, eewAlert=true (boundary + filter target)
        channels.put(1008L, new Channel(
                300L, 1008L, null,
                true, false, false, false,
                SeismicIntensity.ONE,
                null,
                "ja_jp"
        ));

        return channels;
    }

    @BeforeEach
    void setUp() throws IOException {
        // Setup Gson with SeismicIntensity serialization
        this.gson = new GsonBuilder()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
                .create();

        // Create JSON registry with test data
        Path jsonPath = this.tempDir.resolve("channels.json");

        // Write test data to JSON file
        ConcurrentHashMap<Long, Channel> jsonData = new ConcurrentHashMap<>(TEST_CHANNELS);
        String jsonContent = this.gson.toJson(jsonData);
        Files.writeString(jsonPath, jsonContent);

        this.jsonRegistry = new ChannelRegistryJson(jsonPath, this.gson);
        this.jsonRegistry.load(false);

        // Create SQLite registry
        Path dbPath = this.tempDir.resolve("test.db");
        this.sqlRegistry = ChannelRegistrySql.forSQLite(dbPath);

        // Initialize database schema
        DatabaseInitializer.migrate(this.sqlRegistry.getDataSource(), SQLDialect.SQLITE);

        // Use actual migration logic to migrate data from JSON to SQL
        List<Map.Entry<Long, Channel>> entries = ChannelMigration.collectChannels(this.jsonRegistry);
        ChannelMigration.migrateChannelsSql(entries, this.sqlRegistry);
    }

    @AfterEach
    void tearDown() {
        if (this.sqlRegistry != null) {
            this.sqlRegistry.close();
        }
    }

    // ===== get(long key) tests =====

    @Test
    @DisplayName("get() should return equal Channel for all test channels")
    void testGet_allChannels() {
        for (Long targetId : TEST_CHANNELS.keySet()) {
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
        for (Long targetId : TEST_CHANNELS.keySet()) {
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

        assertThat(new HashSet<>(sqlResult))
                .as("Webhook absent channels")
                .isEqualTo(new HashSet<>(jsonResult));

        // Verify expected channels (1001, 1004, 1006, 1008)
        assertThat(sqlResult).containsExactlyInAnyOrder(1001L, 1004L, 1006L, 1008L);
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
                .hasSize(TEST_CHANNELS.size());

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
        // Guild 100 has channels 1001, 1002, 1003
        int jsonRemoved = this.jsonRegistry.removeByGuildId(100L);
        int sqlRemoved = this.sqlRegistry.removeByGuildId(100L);

        assertThat(sqlRemoved)
                .as("Removed count for guild 100")
                .isEqualTo(jsonRemoved)
                .isEqualTo(3);

        // Verify channels are removed
        assertThat(this.jsonRegistry.exists(1001L)).isFalse();
        assertThat(this.jsonRegistry.exists(1002L)).isFalse();
        assertThat(this.jsonRegistry.exists(1003L)).isFalse();

        assertThat(this.sqlRegistry.exists(1001L)).isFalse();
        assertThat(this.sqlRegistry.exists(1002L)).isFalse();
        assertThat(this.sqlRegistry.exists(1003L)).isFalse();

        // Verify other channels are not affected
        assertThat(this.jsonRegistry.exists(1004L)).isTrue();
        assertThat(this.sqlRegistry.exists(1004L)).isTrue();
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

    // ===== clearWebhookByBaseUrl() tests =====

    @Test
    @DisplayName("clearWebhookByBaseUrl() should clear webhook from channels")
    void testClearWebhookByBaseUrl() {
        // Webhook 2001 is used by channel 1002
        assertThat(this.jsonRegistry.get(1002L).getWebhook()).isNotNull();
        assertThat(this.sqlRegistry.get(1002L).getWebhook()).isNotNull();

        // Use URL format for clearing
        String webhookUrl = "https://discord.com/api/webhooks/2001/token_2001";
        int jsonCleared = this.jsonRegistry.clearWebhookByBaseUrl(webhookUrl);
        int sqlCleared = this.sqlRegistry.clearWebhookByBaseUrl(webhookUrl);

        assertThat(sqlCleared)
                .as("Cleared count for webhook 2001")
                .isEqualTo(jsonCleared)
                .isEqualTo(1);

        // Verify webhook is cleared
        assertThat(this.jsonRegistry.get(1002L).getWebhook()).isNull();
        assertThat(this.sqlRegistry.get(1002L).getWebhook()).isNull();

        // Verify other webhooks are not affected
        assertThat(this.jsonRegistry.get(1003L).getWebhook()).isNotNull();
        assertThat(this.sqlRegistry.get(1003L).getWebhook()).isNotNull();
    }

    @Test
    @DisplayName("clearWebhookByBaseUrl() for non-existent webhook should return 0")
    void testClearWebhookByBaseUrl_nonExistentWebhook() {
        String webhookUrl = "https://discord.com/api/webhooks/99999/nonexistent_token";
        int jsonCleared = this.jsonRegistry.clearWebhookByBaseUrl(webhookUrl);
        int sqlCleared = this.sqlRegistry.clearWebhookByBaseUrl(webhookUrl);

        assertThat(sqlCleared)
                .as("Cleared count for non-existent webhook")
                .isEqualTo(jsonCleared)
                .isEqualTo(0);
    }

    // ===== setLangByGuildId() tests =====

    @Test
    @DisplayName("setLangByGuildId() should update language for all channels in guild")
    void testSetLangByGuildId() {
        // Guild 100 has channels 1001, 1002, 1003 with different languages
        int jsonUpdated = this.jsonRegistry.setLangByGuildId(100L, "ko_kr");
        int sqlUpdated = this.sqlRegistry.setLangByGuildId(100L, "ko_kr");

        assertThat(sqlUpdated)
                .as("Updated count for guild 100")
                .isEqualTo(jsonUpdated)
                .isEqualTo(3);

        // Verify language is updated
        assertThat(this.jsonRegistry.get(1001L).getLang()).isEqualTo("ko_kr");
        assertThat(this.jsonRegistry.get(1002L).getLang()).isEqualTo("ko_kr");
        assertThat(this.jsonRegistry.get(1003L).getLang()).isEqualTo("ko_kr");

        assertThat(this.sqlRegistry.get(1001L).getLang()).isEqualTo("ko_kr");
        assertThat(this.sqlRegistry.get(1002L).getLang()).isEqualTo("ko_kr");
        assertThat(this.sqlRegistry.get(1003L).getLang()).isEqualTo("ko_kr");

        // Verify other channels are not affected
        assertThat(this.jsonRegistry.get(1004L).getLang()).isEqualTo("zh_tw");
        assertThat(this.sqlRegistry.get(1004L).getLang()).isEqualTo("zh_tw");
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
    @DisplayName("getChannelsPartitionedByWebhookPresent() with empty filter")
    void testGetChannelsPartitionedByWebhookPresent_emptyFilter() {
        ChannelFilter filter = ChannelFilter.builder().build();

        Map<Boolean, Map<Long, ChannelBase>> jsonResult = this.jsonRegistry.getChannelsPartitionedByWebhookPresent(filter);
        Map<Boolean, Map<Long, ChannelBase>> sqlResult = this.sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

        // Compare key sets
        assertThat(sqlResult.get(true).keySet())
                .as("Channels with webhook")
                .isEqualTo(jsonResult.get(true).keySet());

        assertThat(sqlResult.get(false).keySet())
                .as("Channels without webhook")
                .isEqualTo(jsonResult.get(false).keySet());

        // Deep compare ChannelBase fields (not full equality since JSON returns Channel, SQL returns ChannelBase)
        for (Long targetId : sqlResult.get(true).keySet()) {
            ChannelBase sqlBase = sqlResult.get(true).get(targetId);
            ChannelBase jsonBase = jsonResult.get(true).get(targetId);
            assertChannelBaseFieldsEqual(sqlBase, jsonBase, targetId, "webhook-present");
        }

        for (Long targetId : sqlResult.get(false).keySet()) {
            ChannelBase sqlBase = sqlResult.get(false).get(targetId);
            ChannelBase jsonBase = jsonResult.get(false).get(targetId);
            assertChannelBaseFieldsEqual(sqlBase, jsonBase, targetId, "webhook-absent");
        }
    }

    private void assertChannelBaseFieldsEqual(ChannelBase sql, ChannelBase json, Long targetId, String context) {
        assertThat(sql.isGuild())
                .as("isGuild for %s channel %d", context, targetId)
                .isEqualTo(json.isGuild());
        assertThat(sql.getGuildId())
                .as("guildId for %s channel %d", context, targetId)
                .isEqualTo(json.getGuildId());
        assertThat(sql.getChannelId())
                .as("channelId for %s channel %d", context, targetId)
                .isEqualTo(json.getChannelId());
        assertThat(sql.getThreadId())
                .as("threadId for %s channel %d", context, targetId)
                .isEqualTo(json.getThreadId());
        assertThat(sql.getWebhook())
                .as("webhook for %s channel %d", context, targetId)
                .isEqualTo(json.getWebhook());
        assertThat(sql.getLang())
                .as("lang for %s channel %d", context, targetId)
                .isEqualTo(json.getLang());
    }

    @Test
    @DisplayName("getChannelsPartitionedByWebhookPresent() with hasGuild filter")
    void testGetChannelsPartitionedByWebhookPresent_hasGuildFilter() {
        ChannelFilter filter = ChannelFilter.builder().hasGuild(true).build();

        Map<Boolean, Map<Long, ChannelBase>> jsonResult = this.jsonRegistry.getChannelsPartitionedByWebhookPresent(filter);
        Map<Boolean, Map<Long, ChannelBase>> sqlResult = this.sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

        assertThat(sqlResult.get(true).keySet())
                .as("Guild channels with webhook")
                .isEqualTo(jsonResult.get(true).keySet());

        assertThat(sqlResult.get(false).keySet())
                .as("Guild channels without webhook")
                .isEqualTo(jsonResult.get(false).keySet());
    }

    @Test
    @DisplayName("getChannelsPartitionedByWebhookPresent() with eewAlert filter")
    void testGetChannelsPartitionedByWebhookPresent_eewAlertFilter() {
        ChannelFilter filter = ChannelFilter.builder().eewAlert(true).build();

        Map<Boolean, Map<Long, ChannelBase>> jsonResult = this.jsonRegistry.getChannelsPartitionedByWebhookPresent(filter);
        Map<Boolean, Map<Long, ChannelBase>> sqlResult = this.sqlRegistry.getChannelsPartitionedByWebhookPresent(filter);

        assertThat(sqlResult.get(true).keySet())
                .as("EewAlert channels with webhook")
                .isEqualTo(jsonResult.get(true).keySet());

        assertThat(sqlResult.get(false).keySet())
                .as("EewAlert channels without webhook")
                .isEqualTo(jsonResult.get(false).keySet());
    }

    // ===== isWebhookForThread(long webhookId, long threadId) tests =====

    @Test
    @DisplayName("isWebhookForThread() for webhook used by single destination")
    void testIsWebhookForThread_singleDestination() {
        // Webhook 2001 is used only by channel 1002
        boolean jsonResult = this.jsonRegistry.isWebhookForThread(2001L, 1002L);
        boolean sqlResult = this.sqlRegistry.isWebhookForThread(2001L, 1002L);

        assertThat(sqlResult)
                .as("isWebhookForThread(2001, 1002)")
                .isEqualTo(jsonResult)
                .isTrue();
    }

    @Test
    @DisplayName("isWebhookForThread() for webhook used by different destination")
    void testIsWebhookForThread_differentDestination() {
        // Webhook 2001 is used by channel 1002, not by 1003
        boolean jsonResult = this.jsonRegistry.isWebhookForThread(2001L, 1003L);
        boolean sqlResult = this.sqlRegistry.isWebhookForThread(2001L, 1003L);

        assertThat(sqlResult)
                .as("isWebhookForThread(2001, 1003)")
                .isEqualTo(jsonResult)
                .isFalse();
    }

    @Test
    @DisplayName("isWebhookForThread() for non-existent webhook")
    void testIsWebhookForThread_nonExistentWebhook() {
        boolean jsonResult = this.jsonRegistry.isWebhookForThread(99999L, 1001L);
        boolean sqlResult = this.sqlRegistry.isWebhookForThread(99999L, 1001L);

        assertThat(sqlResult)
                .as("isWebhookForThread(99999, 1001)")
                .isEqualTo(jsonResult)
                .isTrue(); // No conflict found
    }

    // ===== Null value handling tests =====

    @Test
    @DisplayName("Channel with null lang should be handled correctly")
    void testNullLangChannel() {
        Channel jsonChannel = this.jsonRegistry.get(1006L);
        Channel sqlChannel = this.sqlRegistry.get(1006L);

        assertThat(jsonChannel.getLang()).isNull();
        assertThat(sqlChannel.getLang()).isNull();
        assertThat(sqlChannel).isEqualTo(jsonChannel);
    }

    @Test
    @DisplayName("Channel with null guildId should be handled correctly")
    void testNullGuildIdChannel() {
        // Channel 1004 is DM (guildId=null)
        Channel jsonChannel = this.jsonRegistry.get(1004L);
        Channel sqlChannel = this.sqlRegistry.get(1004L);

        assertThat(jsonChannel.getGuildId()).isNull();
        assertThat(sqlChannel.getGuildId()).isNull();
        assertThat(sqlChannel).isEqualTo(jsonChannel);

        // Channel 1006 also has null guildId
        Channel jsonChannel6 = this.jsonRegistry.get(1006L);
        Channel sqlChannel6 = this.sqlRegistry.get(1006L);

        assertThat(jsonChannel6.getGuildId()).isNull();
        assertThat(sqlChannel6.getGuildId()).isNull();
        assertThat(sqlChannel6).isEqualTo(jsonChannel6);
    }

    // ===== Thread channel tests =====

    @Test
    @DisplayName("Thread channel should have correct channelId and threadId")
    void testThreadChannel() {
        Channel jsonChannel = this.jsonRegistry.get(1003L);
        Channel sqlChannel = this.sqlRegistry.get(1003L);

        // Thread channel: targetId=1003, channelId=1000, threadId=1003
        assertThat(jsonChannel.getChannelId()).isEqualTo(1000L);
        assertThat(jsonChannel.getThreadId()).isEqualTo(1003L);

        assertThat(sqlChannel.getChannelId()).isEqualTo(1000L);
        assertThat(sqlChannel.getThreadId()).isEqualTo(1003L);

        assertThat(sqlChannel).isEqualTo(jsonChannel);
    }

    // ===== Edge case: UNKNOWN intensity =====

    @Test
    @DisplayName("Channel with UNKNOWN intensity should be handled correctly")
    void testUnknownIntensityChannel() {
        Channel jsonChannel = this.jsonRegistry.get(1007L);
        Channel sqlChannel = this.sqlRegistry.get(1007L);

        assertThat(jsonChannel.getMinIntensity()).isEqualTo(SeismicIntensity.UNKNOWN);
        assertThat(sqlChannel.getMinIntensity()).isEqualTo(SeismicIntensity.UNKNOWN);
        assertThat(sqlChannel).isEqualTo(jsonChannel);
    }
}
