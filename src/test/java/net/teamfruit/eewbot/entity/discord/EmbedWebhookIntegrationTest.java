package net.teamfruit.eewbot.entity.discord;

import com.google.gson.JsonObject;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in: integrationTest --tests '*EmbedWebhookIntegrationTest', with DISCORD_WEBHOOK_URL. */
@Tag("integration")
class EmbedWebhookIntegrationTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"webhook", "message"})
    void packedMessagesAreAcceptedByBothEndpoints(final String transport) throws InterruptedException {
        final String url = System.getenv("DISCORD_WEBHOOK_URL");
        assumeTrue(url != null && !url.isBlank(), "DISCORD_WEBHOOK_URL not set");
        final boolean messageCreate = transport.equals("message");
        final String token = System.getenv("DISCORD_BOT_TOKEN");
        final String channel = System.getenv("DISCORD_TEST_CHANNEL_ID");
        if (messageCreate) {
            assumeTrue(token != null && !token.isBlank(), "DISCORD_BOT_TOKEN not set");
            assumeTrue(channel != null && channel.matches("[0-9]+"), "DISCORD_TEST_CHANNEL_ID not set");
        }
        final URI uri = URI.create(messageCreate ? "https://discord.com/api/v10/channels/" + channel + "/messages"
                : url + (url.contains("?") ? "&" : "?") + "wait=true");
        final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        final String image = "https://cdn.discordapp.com/embed/avatars/0.png";
        final List<PendingField> fields = new ArrayList<>();
        for (int i = 0; i < 25; i++)
            fields.add(new PendingField("名".repeat(256), "あ".repeat(1024), i % 2 == 0));
        final List<PendingEmbed> sources = List.of(
                new PendingEmbed("EEWBot embed boundary test", "あ".repeat(8000), null,
                        Instant.parse("2026-10-19T00:00:00.123456789Z"), Color.RED,
                        "footer", image, image, image, "author", "https://example.com/", image, fields),
                PendingEmbed.empty().withDescription("<&='\"\\\n\u2028\u2029".repeat(300)),
                PendingEmbed.empty().withFields(List.of(new PendingField("emoji", "😀\n".repeat(1200), false))));
        int index = 0;
        for (final List<PendingEmbed> message : EmbedPacker.pack(sources)) {
            assertThat(EmbedWebhookCost.fits(message)).isTrue();
            final DiscordWebhook webhook = DiscordWebhook.builder().embeds(EmbedRenderer.toDiscordEmbeds(message)).build();
            if (!messageCreate) {
                webhook.username = "😀".repeat(80);
                webhook.avatar_url = image;
            }
            final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (messageCreate)
                requestBuilder.header("Authorization", "Bot " + token);
            final HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(Codecs.GSON.toJson(webhook))).build();
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < 6; attempt++) {
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException e) {
                    // HttpClient exceptions can include the webhook token in the URI.
                    throw new AssertionError("Discord request failed (connection details omitted)");
                }
                if (response.statusCode() != 429)
                    break;
                final JsonObject body = Codecs.GSON.fromJson(response.body(), JsonObject.class);
                Thread.sleep((long) (body.get("retry_after").getAsDouble() * 1000) + 200);
            }
            assertThat(response).isNotNull();
            assertThat(response.statusCode()).as("%s packed message %s, effective cost %s", transport, index++,
                    EmbedWebhookCost.estimate(message)).isEqualTo(200);
            Thread.sleep(500);
        }
    }
}
