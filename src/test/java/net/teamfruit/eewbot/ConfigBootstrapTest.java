package net.teamfruit.eewbot;

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
        return EEWBotFactory.loadOrMigrateConfig(registry);
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
    void v1ConfigIsMigratedAndBackedUp() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "token": "v1-token",
                  "enableKyoshin": true,
                  "kyoshinDelay": 3,
                  "dmdataAPIKey": "v1-dmdata-key",
                  "nptServer": "ntp.nict.jp",
                  "defaultLanuage": "en_us"
                }
                """);

        ConfigV2 config = bootstrap(configPath);

        assertEquals("v1-token", config.getBase().getDiscordToken());
        assertEquals("v1-dmdata-key", config.getDmdata().getAPIKey());
        assertTrue(config.getLegacy().isEnableKyoshin());
        assertEquals(3, config.getLegacy().getKyoshinDelay());
        assertEquals("ntp.nict.jp", config.getLegacy().getNtpServer());

        Path backup = this.tempDir.resolve("config.json.v1.bak");
        assertTrue(Files.exists(backup), "the original V1 config must be backed up");
        assertTrue(Files.readString(backup).contains("v1-token"));

        assertTrue(Files.readString(configPath).contains("\"base\""));
    }

    @Test
    void missingConfigCreatesDefaults() throws IOException {
        Path configPath = this.tempDir.resolve("config.json");

        ConfigV2 config = bootstrap(configPath);

        assertTrue(Files.exists(configPath));
        assertEquals("", config.getBase().getDiscordToken());
    }
}
