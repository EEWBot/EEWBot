package net.teamfruit.eewbot.registry;

import discord4j.common.util.Snowflake;
import discord4j.core.object.emoji.CustomEmoji;
import discord4j.core.object.entity.ApplicationEmoji;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.spec.ApplicationEmojiCreateSpec;
import discord4j.rest.util.Image;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Application-owned intensity emojis.
 * Resolution may complete after delivery starts; until then lookups are empty and embeds omit the icon.
 */
public final class IntensityEmojiRegistry {

    private static final String ICON_BASE_URL = "https://resources.eewbot.net/intensity-icon/";

    private volatile Map<SeismicIntensity, CustomEmoji> emojis;

    private IntensityEmojiRegistry(Map<SeismicIntensity, CustomEmoji> emojis) {
        this.emojis = Map.copyOf(emojis);
    }

    public static IntensityEmojiRegistry empty() {
        return new IntensityEmojiRegistry(Map.of());
    }

    /** Resolves synchronously. Listing failure is propagated; a single emoji failure only omits that emoji. */
    public static IntensityEmojiRegistry initialize(ApplicationInfo application, HttpClient httpClient) throws IOException {
        return new IntensityEmojiRegistry(resolve(application, httpClient));
    }

    /**
     * Returns an empty registry immediately and resolves on a daemon thread so startup never waits on
     * emoji listing, icon downloads or emoji creation. Failures are logged and leave the registry empty.
     */
    public static IntensityEmojiRegistry initializeInBackground(ApplicationInfo application, HttpClient httpClient) {
        IntensityEmojiRegistry registry = empty();
        Thread thread = new Thread(() -> {
            try {
                registry.emojis = Map.copyOf(resolve(application, httpClient));
                Log.logger.info("Intensity emojis resolved: {}", registry.emojis.size());
            } catch (Exception e) {
                Log.logger.warn("Failed to resolve intensity emojis; embeds will omit intensity icons", e);
            }
        }, "eewbot-intensity-emoji");
        thread.setDaemon(true);
        thread.start();
        return registry;
    }

    /** Reuses matching names without fetching or comparing their images. */
    private static Map<SeismicIntensity, CustomEmoji> resolve(ApplicationInfo application, HttpClient httpClient) throws IOException {
        Map<String, ApplicationEmoji> existing = application.getEmojis()
                .collectMap(ApplicationEmoji::getName)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Failed to list application emojis"));
        Map<SeismicIntensity, CustomEmoji> resolved = new EnumMap<>(SeismicIntensity.class);
        for (SeismicIntensity intensity : SeismicIntensity.values()) {
            String iconName = intensity.getIconName();
            if (iconName == null) continue;

            String name = "intensity_" + iconName.replace('-', '_');
            ApplicationEmoji emoji = existing.get(name);
            if (emoji == null) {
                try {
                    Image image = downloadImage(httpClient, iconName);
                    emoji = application.createEmoji(ApplicationEmojiCreateSpec.of(name, image))
                            .blockOptional()
                            .orElseThrow(() -> new IllegalStateException("Failed to create application emoji: " + name));
                } catch (IOException | RuntimeException e) {
                    if (Thread.currentThread().isInterrupted()) throw e;
                    Log.logger.warn("Failed to register intensity emoji {}; continuing without it", name, e);
                    continue;
                }
            }
            resolved.put(intensity, CustomEmoji.of(emoji.getId(), emoji.getName(), emoji.isAnimated()));
        }
        return resolved;
    }

    private static Image downloadImage(HttpClient httpClient, String iconName) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ICON_BASE_URL + iconName + ".png"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading intensity icon: " + iconName, e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download intensity icon " + iconName + ": HTTP " + response.statusCode());
        }
        return Image.ofRaw(response.body(), Image.Format.PNG);
    }

    /** Empty for intensities without an icon, including UNKNOWN. */
    public Optional<Snowflake> getId(SeismicIntensity intensity) {
        return Optional.ofNullable(this.emojis.get(intensity)).map(CustomEmoji::getId);
    }

    /** CDN WebP image URL, suitable for embed thumbnails. */
    public Optional<String> getImageUrl(SeismicIntensity intensity) {
        return this.getId(intensity).map(id -> "https://cdn.discordapp.com/emojis/" + id.asString() + ".webp");
    }

    /** Discord's inline emoji syntax, suitable for embed text. */
    public Optional<String> getMention(SeismicIntensity intensity) {
        return Optional.ofNullable(this.emojis.get(intensity)).map(CustomEmoji::asFormat);
    }
}
