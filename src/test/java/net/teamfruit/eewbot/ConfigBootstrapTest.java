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
        JsonRegistry<ConfigV2> registry = new JsonRegistry<>(configPath, ConfigV2::new, ConfigV2.class, Codecs.GSON_PRETTY);
        return EEWBotFactory.loadConfig(registry);
    }

    private Path backupOf(Path configPath) {
        return configPath.resolveSibling(configPath.getFileName() + ".bak");
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

        assertTrue(Files.readString(backupOf(configPath)).contains("redis://localhost:6379"),
                "the dropped redis section must survive in the backup");
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
        assertTrue(Files.readString(backupOf(configPath)).contains("typoSection"),
                "the dropped section must survive in the backup");
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

        assertTrue(Files.readString(backupOf(configPath)).contains("ntp.nict.jp"),
                "the dropped legacy section must survive in the backup");
    }

    @Test
    void v1ConfigIsBackedUpBeforeBeingReset() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "token": "v1-token",
                  "dmdataAPIKey": "v1-dmdata-key",
                  "nptServer": "ntp.nict.jp",
                  "defaultLanuage": "en_us"
                }
                """);

        ConfigV2 config = bootstrap(configPath);

        // The V1 schema is no longer understood, so nothing is carried over and startup fails validation.
        assertFalse(config.isValid());

        assertEquals("v1-token", Codecs.GSON_PRETTY.fromJson(Files.readString(backupOf(configPath)), JsonObject.class)
                .get("token").getAsString(), "the unsupported config must survive in the backup");
    }

    @Test
    void configWithoutDatabaseTypeDefaultsToSqlite() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "base": { "discordToken": "my-token" },
                  "dmdata": { "apiKey": "my-dmdata-key" }
                }
                """);

        ConfigV2 config = bootstrap(configPath);

        assertEquals("my-token", config.getBase().getDiscordToken());
        assertEquals("sqlite", config.getDatabase().getType());
        assertTrue(Files.readString(configPath).contains("\"sqlite\""),
                "the defaulted database type must be written back to the file");
    }

    @Test
    void alreadyNormalizedConfigIsNotRewritten() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        bootstrap(configPath);
        String created = Files.readString(configPath);

        bootstrap(configPath);

        assertEquals(created, Files.readString(configPath), "a normalized config must be left as is");
        assertFalse(Files.exists(backupOf(configPath)), "no backup should be written when nothing changes");
    }

    @Test
    void missingConfigCreatesDefaults() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");

        ConfigV2 config = bootstrap(configPath);

        assertTrue(Files.exists(configPath));
        assertEquals("", config.getBase().getDiscordToken());
        assertFalse(Files.exists(backupOf(configPath)), "creating a fresh config must not write a backup");
    }
}
