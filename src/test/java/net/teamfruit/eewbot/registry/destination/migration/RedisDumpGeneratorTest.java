package net.teamfruit.eewbot.registry.destination.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.JsonObjectMapper;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.search.IndexDefinition;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag("integration")
@Disabled("Run manually to regenerate dump.rdb for Redis migration tests")
@Testcontainers(disabledWithoutDocker = true)
class RedisDumpGeneratorTest {

    static final String REDIS_IMAGE = "redis/redis-stack-server:7.4.0-v3";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    @Test
    void generateDumpRdb() throws IOException, InterruptedException {
        JedisPooled jedis = new JedisPooled(REDIS.getHost(), REDIS.getFirstMappedPort());

        // Use a passthrough JsonObjectMapper so raw JSON strings are sent as-is to Redis
        jedis.setJsonObjectMapper(new JsonObjectMapper() {
            private final Gson gson = new Gson();

            @Override
            public <T> T fromJson(String s, Class<T> aClass) {
                return this.gson.fromJson(s, aClass);
            }

            @Override
            public String toJson(Object o) {
                // When o is already a String (raw JSON), return it directly
                return (String) o;
            }
        });

        // Create RediSearch index matching ChannelRegistryRedis.createJedisIndex()
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
                .setPrefixes("channel:");
        jedis.ftCreate("channel-index", IndexOptions.defaultOptions().setDefinition(indexDefinition), schema);

        // Load old-format JSON and store each entry as a Redis JSON document
        Gson gson = new GsonBuilder().create();
        JsonObject root;
        try (InputStream is = getClass().getResourceAsStream("/migration/channels_old_format.json");
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            root = gson.fromJson(reader, JsonObject.class);
        }

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = "channel:" + entry.getKey();
            String rawJson = gson.toJson(entry.getValue());
            jedis.jsonSet(key, Path.ROOT_PATH, rawJson);
        }

        // Trigger RDB save via BGSAVE and poll until completion
        REDIS.execInContainer("redis-cli", "BGSAVE");
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            String info = REDIS.execInContainer("redis-cli", "INFO", "persistence").getStdout();
            if (info.contains("rdb_bgsave_in_progress:0")) {
                if (info.contains("rdb_last_bgsave_status:ok")) break;
                throw new AssertionError("BGSAVE completed but status is not ok:\n" + info);
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
        if (System.currentTimeMillis() >= deadline) {
            throw new AssertionError("BGSAVE did not complete within 30 seconds");
        }

        // Copy dump.rdb from container to test resources
        java.nio.file.Path outputPath = Paths.get("src/test/resources/migration/dump.rdb");
        Files.createDirectories(outputPath.getParent());
        REDIS.copyFileFromContainer("/data/dump.rdb", outputPath.toString());

        System.out.println("Generated dump.rdb at: " + outputPath.toAbsolutePath());
        System.out.println("Channels stored: " + root.size());

        jedis.close();
    }
}
