package net.teamfruit.eewbot.entity.jma.telegram;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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

    private static URI webhookUri;
    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() throws URISyntaxException {
        String webhookUrl = System.getenv("DISCORD_WEBHOOK_URL");
        assumeTrue(webhookUrl != null && !webhookUrl.isEmpty(), "DISCORD_WEBHOOK_URL not set, skipping");

        webhookUri = new URIBuilder(URI.create(webhookUrl))
                .addParameter("wait", "true")
                .build();
        httpClient = HttpClients.createDefault();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    static Stream<Arguments> provideAllDiscordJsonFiles() throws IOException {
        Stream.Builder<Arguments> builder = Stream.builder();
        for (String type : TELEGRAM_TYPES) {
            Path dir = Paths.get("src/test/resources/jmaxml/" + type);
            if (!Files.exists(dir)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(p -> p.getFileName().toString().endsWith("_discord_expected.json"))
                        .sorted()
                        .forEach(p -> {
                            String fileName = p.getFileName().toString();
                            String baseName = fileName.replace("_discord_expected.json", "");
                            builder.add(Arguments.of(type, baseName));
                        });
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("provideAllDiscordJsonFiles")
    void sendDiscordWebhook(String type, String baseName) throws Exception {
        String jsonPath = "jmaxml/" + type + "/" + baseName + "_discord_expected.json";
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

    private int[] executePost(String json) throws IOException {
        HttpPost post = new HttpPost(webhookUri);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        return httpClient.execute(post, response -> {
            int code = response.getCode();
            long retryAfterMs = 0;
            if (code == 429) {
                Header retryAfterHeader = response.getFirstHeader("Retry-After");
                if (retryAfterHeader != null) {
                    retryAfterMs = (long) (Double.parseDouble(retryAfterHeader.getValue()) * 1000);
                }
            }
            return new int[]{code, (int) retryAfterMs};
        });
    }
}
