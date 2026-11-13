package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.webhooksender.WebhookSenderRequest;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.junit.jupiter.api.Test;
import reactor.adapter.JdkFlowAdapter;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebhookSenderClientTest {

    @Test
    void localTestAndCoreDeliveryUseTheSameTransportAndPreserveQueryAndHeader() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        List<HttpRequest> requests = new ArrayList<>();
        doAnswer(call -> {
            requests.add(call.getArgument(0));
            return response;
        }).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        ConfigV2.WebhookSender config = new ConfigV2.WebhookSender();
        config.setAddress("http://sender.test/prefix?token=value");
        config.setCustomHeader(" Authorization : Bearer test:with-colon ");
        WebhookSenderClient client = new WebhookSenderClient(http, config);
        String url = "https://discord.com/api/webhooks/1234567890123456789/test?thread_id=1234567890123456790";
        WebhookSenderRequest payload = new WebhookSenderRequest(List.of(url), DiscordWebhook.builder().build());

        client.send(List.of(payload));
        assertThat(client.sendSingle(payload)).isEqualTo(204);
        client.getNotFounds();

        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).uri()).isEqualTo(URI.create("http://sender.test/api/send?token=value"));
        assertThat(requests.get(1).uri()).isEqualTo(requests.get(0).uri());
        assertThat(requests.get(2).uri()).isEqualTo(URI.create("http://sender.test/api/notfounds?token=value"));
        assertThat(requests.get(2).method()).isEqualTo("GET");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.headers().firstValue("Authorization")).contains("Bearer test:with-colon");
            assertThat(request.headers().firstValue("User-Agent")).contains("EEWBot");
        });
        assertThat(bodyOf(requests.get(1))).isEqualTo(bodyOf(requests.get(0)));
        var body = Codecs.GSON.fromJson(bodyOf(requests.get(1)), com.google.gson.JsonArray.class);
        assertThat(body.get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsString()).isEqualTo(url);
        assertThat(body.get(0).getAsJsonObject().get("retry_limit").getAsInt()).isEqualTo(10);
    }

    private static String bodyOf(HttpRequest request) {
        return JdkFlowAdapter.flowPublisherToFlux(request.bodyPublisher().orElseThrow())
                .reduce(new ByteArrayOutputStream(), (output, buffer) -> {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    output.writeBytes(bytes);
                    return output;
                }).map(output -> output.toString(StandardCharsets.UTF_8)).block(Duration.ofSeconds(1));
    }
}
