package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.webhooksender.WebhookSenderRequest;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/** Existing webhook-sender transport shared by delivery and local Interactions. */
public final class WebhookSenderClient {

    private final HttpClient httpClient;
    private final URI address;
    private final String[] header;

    public WebhookSenderClient(HttpClient httpClient, ConfigV2.WebhookSender config) {
        this.httpClient = httpClient;
        this.header = parseHeader(config.getCustomHeader());
        this.address = StringUtils.isNotEmpty(config.getAddress()) ? URI.create(config.getAddress()) : null;
    }

    public boolean isConfigured() {
        return this.address != null;
    }

    public HttpResponse<String> send(List<WebhookSenderRequest> requests) throws IOException, InterruptedException {
        return this.httpClient.send(sendRequest(requests), HttpResponse.BodyHandlers.ofString());
    }

    public int sendSingle(WebhookSenderRequest request) throws IOException, InterruptedException {
        return this.httpClient.send(sendRequest(List.of(request)), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    public HttpResponse<String> getNotFounds() throws IOException, InterruptedException {
        return this.httpClient.send(request("/api/notfounds").GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest sendRequest(List<WebhookSenderRequest> requests) {
        return request("/api/send")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Codecs.GSON.toJson(requests)))
                .build();
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(replacePathPreservingQuery(this.address, path))
                .header("User-Agent", "EEWBot");
        if (this.header.length > 0) builder.headers(this.header);
        return builder;
    }

    private static URI replacePathPreservingQuery(URI base, String path) {
        try {
            return new URI(base.getScheme(), base.getRawAuthority(), path, base.getRawQuery(), null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + base + " with path " + path, e);
        }
    }

    private static String[] parseHeader(String customHeader) {
        if (StringUtils.isBlank(customHeader)) return new String[0];
        String[] header = customHeader.split(":", 2);
        if (header.length != 2 || StringUtils.isBlank(header[0]) || StringUtils.isBlank(header[1])) {
            Log.logger.warn("Ignoring invalid webhook sender custom header");
            return new String[0];
        }
        return new String[]{header[0].trim(), header[1].trim()};
    }
}
