package net.teamfruit.eewbot;

import com.google.gson.JsonObject;
import net.teamfruit.eewbot.registry.JsonRegistry;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigBootstrapTest {

    @TempDir
    Path tempDir;

    private ConfigV2 bootstrap(Path configPath) throws IOException {
        return bootstrap(configPath, this.tempDir.resolve("channels.json"));
    }

    private ConfigV2 bootstrap(Path configPath, Path channelsJsonPath) throws IOException {
        JsonRegistry<ConfigV2> registry = new JsonRegistry<>(configPath, ConfigV2::new, ConfigV2.class, Codecs.GSON_PRETTY);
        return EEWBotFactory.loadConfig(registry, channelsJsonPath);
    }

    @Test
    void v2ConfigWithRemovedRedisSectionIsPreserved() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token", "defaultLanguage": "en_US" },
                  "database": { "type": "postgresql", "postgresql": { "host": "db.example.com" } },
                  "redis": { "address": "redis://localhost:6379" },
                  "dmdata": { "apiKey": "my-dmdata-key" },
                  "renderer": { "address": "http://renderer:8080", "key": "renderer-key" }
                }
                """);

        ConfigV2 config = bootstrap(configPath);

        assertEquals("my-token", config.getBase().getDiscordToken());
        assertEquals("en_US", config.getBase().getDefaultLanguage());
        assertEquals("postgresql", config.getDatabase().getType());
        assertEquals("db.example.com", config.getDatabase().getPostgresql().getHost());
        assertEquals("my-dmdata-key", config.getDmdata().getAPIKey());
        assertEquals("http://renderer:8080", config.getRenderer().getAddress());

        String saved = Files.readString(configPath);
        assertTrue(saved.contains("my-token"), "config.json must keep the configured token");
        assertFalse(saved.contains("redis"), "the removed redis section should be dropped on save");
    }

    @Test
    void unknownFieldDoesNotResetConfig() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "typoSection": { "foo": 1 }
                }
                """);

        ConfigV2 config = bootstrap(configPath);

        assertEquals("my-token", config.getBase().getDiscordToken());
    }

    @Test
    void v2ConfigWithRemovedLegacySectionIsPreserved() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "dmdata": { "apiKey": "my-dmdata-key" },
                  "legacy": { "enableKyoshin": true, "ntpServer": "ntp.nict.jp" }
                }
                """);

        ConfigV2 config = bootstrap(configPath);

        assertEquals("my-token", config.getBase().getDiscordToken());
        assertEquals("my-dmdata-key", config.getDmdata().getAPIKey());

        String saved = Files.readString(configPath);
        assertFalse(saved.contains("legacy"), "the removed legacy section should be dropped on save");
    }

    @Test
    void v1ConfigFailsWithMigrationHint() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "token": "v1-token",
                  "dmdataAPIKey": "v1-dmdata-key",
                  "nptServer": "ntp.nict.jp",
                  "defaultLanuage": "en_us"
                }
                """);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bootstrap(configPath));
        assertTrue(e.getMessage().contains("2.9.x"), "the error must hint at how to migrate");

        assertEquals("v1-token", Codecs.GSON_PRETTY.fromJson(Files.readString(configPath), JsonObject.class)
                .get("token").getAsString(), "the unsupported config must be left untouched");
    }

    @Test
    void configWithoutDatabaseTypeButWithRedisAddressFails() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "dmdata": { "apiKey": "my-dmdata-key" },
                  "redis": { "address": "localhost:6379" }
                }
                """);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bootstrap(configPath));
        assertTrue(e.getMessage().contains("redis"), "the error must name the detected legacy backend");
        assertTrue(e.getMessage().contains("2.9.x"), "the error must hint at how to migrate");

        assertTrue(Files.readString(configPath).contains("redis"),
                "the config must be left untouched so the migration tool can still read it");
    }

    @Test
    void redisDatabaseTypeFailsWithoutRewritingConfig() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "database": { "type": "redis" },
                  "redis": { "address": "redis.example.com:6379" }
                }
                """);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bootstrap(configPath));
        assertTrue(e.getMessage().contains("2.9.x"), "the error must hint at how to migrate");

        assertTrue(Files.readString(configPath).contains("redis.example.com:6379"),
                "the redis address must survive so a rollback to 2.9.x can still migrate");
    }

    @Test
    void jsonDatabaseTypeFailsWithoutRewritingConfig() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "database": { "type": "JSON" }
                }
                """);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bootstrap(configPath));
        assertTrue(e.getMessage().contains("2.9.x"), "the error must hint at how to migrate");

        assertTrue(Files.readString(configPath).contains("\"JSON\""),
                "the unsupported config must be left untouched");
    }

    @Test
    void configWithoutDatabaseTypeButWithChannelsJsonFails() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Path channelsJsonPath = this.tempDir.resolve("channels.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "dmdata": { "apiKey": "my-dmdata-key" }
                }
                """);
        Files.writeString(channelsJsonPath, "{}");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bootstrap(configPath, channelsJsonPath));
        assertTrue(e.getMessage().contains("json"), "the error must name the detected legacy backend");
        assertTrue(e.getMessage().contains("2.9.x"), "the error must hint at how to migrate");
    }

    @Test
    void configWithoutDatabaseTypeAndWithoutLegacyMarkersIsAccepted() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "dmdata": { "apiKey": "my-dmdata-key" }
                }
                """);

        ConfigV2 config = bootstrap(configPath);

        assertEquals("my-token", config.getBase().getDiscordToken());
    }

    @Test
    void explicitDatabaseTypeWinsOverLegacyMarkers() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Path channelsJsonPath = this.tempDir.resolve("channels.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "database": { "type": "sqlite" },
                  "redis": { "address": "localhost:6379" }
                }
                """);
        Files.writeString(channelsJsonPath, "{}");

        ConfigV2 config = bootstrap(configPath, channelsJsonPath);

        assertEquals("sqlite", config.getDatabase().getType());
    }

    @Test
    void missingConfigCreatesDefaults() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");

        ConfigV2 config = bootstrap(configPath);

        assertTrue(Files.exists(configPath));
        assertEquals("", config.getBase().getDiscordToken());
    }
}
