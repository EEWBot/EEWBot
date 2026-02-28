package net.teamfruit.eewbot.registry.destination.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelRegistryJsonClearWebhookTest {

    @TempDir
    Path tempDir;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Subclass that forces save() to throw IOException, simulating a persistence failure.
     */
    static class FailingSaveChannelRegistryJson extends ChannelRegistryJson {
        FailingSaveChannelRegistryJson(Path path, Gson gson) {
            super(path, gson);
        }

        @Override
        public void save() throws IOException {
            throw new IOException("Simulated save failure");
        }
    }

    @Test
    @DisplayName("clearWebhookByUrls should throw UncheckedIOException when save() fails")
    void saveFailureThrowsUncheckedIOException() throws IOException {
        Path jsonPath = this.tempDir.resolve("channels.json");

        // Create a valid JSON file with a webhook-bearing channel
        ChannelWebhook webhook = ChannelWebhook.of(555L, "secretToken");
        Channel channel = new Channel(100L, 1L, null, true, false, false, false,
                false, SeismicIntensity.ONE, webhook, "ja_jp");
        String json = GSON.toJson(java.util.Map.of(1L, channel));
        Files.writeString(jsonPath, json);

        // Load using the failing-save subclass
        FailingSaveChannelRegistryJson registry = new FailingSaveChannelRegistryJson(jsonPath, GSON);
        registry.load(false);

        // clearWebhookByUrls should propagate save failure as UncheckedIOException
        assertThatThrownBy(() -> registry.clearWebhookByUrls(
                List.of("https://discord.com/api/webhooks/555/secretToken")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to persist webhook clearing");
    }
}
