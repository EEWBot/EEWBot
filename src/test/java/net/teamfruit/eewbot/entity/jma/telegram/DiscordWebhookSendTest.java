package net.teamfruit.eewbot.entity.jma.telegram;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class DiscordWebhookSendTest {

    private static final String[] TELEGRAM_TYPES = {"vxse51", "vxse52", "vxse53", "vxse61", "vtse41"};
    private static final String rendererHash = BaseWebhookTest.computeRendererHash(
            System.getenv("EEWBOT_RENDERER_ADDRESS"),
            System.getenv("EEWBOT_RENDERER_KEY")
    );
    private static final String EXPECTED_SUFFIX = rendererHash != null
            ? "_discord_expected_" + rendererHash + ".json"
            : "_discord_expected.json";

    private static URI webhookUri;
    private static HttpClient httpClient;

    @BeforeAll
    static void setUp() {
        String webhookUrl = System.getenv("DISCORD_WEBHOOK_URL");
        assumeTrue(webhookUrl != null && !webhookUrl.isEmpty(), "DISCORD_WEBHOOK_URL not set, skipping");

        String separator = webhookUrl.contains("?") ? "&" : "?";
        webhookUri = URI.create(webhookUrl + separator + "wait=true");
        httpClient = HttpClient.newHttpClient();
    }

    static Stream<Arguments> provideAllDiscordJsonFiles() throws IOException {
        Stream.Builder<Arguments> builder = Stream.builder();
        for (String type : TELEGRAM_TYPES) {
            Path dir = Paths.get("src/test/resources/jmaxml/" + type);
            if (!Files.exists(dir)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(p -> p.getFileName().toString().endsWith(EXPECTED_SUFFIX))
                        .sorted()
                        .forEach(p -> {
                            String fileName = p.getFileName().toString();
                            String baseName = fileName.replace(EXPECTED_SUFFIX, "");
                            builder.add(Arguments.of(type, baseName));
                        });
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("provideAllDiscordJsonFiles")
    void sendDiscordWebhook(String type, String baseName) throws Exception {
        String jsonPath = "jmaxml/" + type + "/" + baseName + EXPECTED_SUFFIX;
        InputStream stream = getClass().getClassLoader().getResourceAsStream(jsonPath);
        assertNotNull(stream, "JSON file not found: " + jsonPath);
        String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        int[] result = executePost(json);
        int statusCode = result[0];
        long retryAfterMs = result[1];

        if (statusCode == 429) {
            long waitMs = retryAfterMs > 0 ? retryAfterMs : 5000;
            System.out.printf("[RATE LIMITED] %s/%s - waiting %dms before retry%n", type, baseName, waitMs);
            Thread.sleep(waitMs);
            result = executePost(json);
            statusCode = result[0];
        }

        assertTrue(statusCode >= 200 && statusCode < 300,
                String.format("Expected 2xx but got %d for %s/%s", statusCode, type, baseName));

        Thread.sleep(1000);
    }

    private int[] executePost(String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(webhookUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        int code = response.statusCode();
        long retryAfterMs = 0;
        if (code == 429) {
            retryAfterMs = response.headers().firstValue("Retry-After")
                    .map(v -> (long) (Double.parseDouble(v) * 1000))
                    .orElse(0L);
        }
        return new int[]{code, (int) retryAfterMs};
    }
}
