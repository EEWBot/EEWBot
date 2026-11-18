package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.QuakeInfoStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JMAXmlGatewayConditionalRequestTest {

    private static final String ROOT = "https://example.com/";
    private static final String LAST_MODIFIED = "Tue, 28 Jul 2026 16:45:00 GMT";
    private static final String ETAG = "\"abc123\"";

    private final List<HttpRequest> feedRequests = new ArrayList<>();
    private Map<String, List<String>> feedHeaders = new HashMap<>();
    private int feedStatus = 200;
    private JMAXmlGateway gateway;

    @BeforeEach
    void setUp() {
        HttpClient client = mock(HttpClient.class);
        when(client.<byte[]>sendAsync(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            this.feedRequests.add(request);
            return CompletableFuture.completedFuture(response(this.feedStatus, this.feedHeaders));
        });
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T16:45:00Z"), ZoneOffset.UTC);
        this.gateway = new JMAXmlGateway(client, new QuakeInfoStore(), report -> {
        }, ROOT, clock);
    }

    @Test
    void firstRequestIsUnconditional() {
        this.gateway.run();
        assertThat(this.feedRequests).hasSize(1);
        assertThat(header(0, "If-None-Match")).isEmpty();
        assertThat(header(0, "If-Modified-Since")).isEmpty();
    }

    @Test
    void sendsIfNoneMatchWhenServerProvidesETag() {
        this.feedHeaders = Map.of("ETag", List.of(ETAG), "Last-Modified", List.of(LAST_MODIFIED));
        this.gateway.run();
        this.feedStatus = 304;
        this.gateway.run();
        assertThat(header(1, "If-None-Match")).contains(ETAG);
        assertThat(header(1, "If-Modified-Since")).contains(LAST_MODIFIED);
    }

    @Test
    void fallsBackToIfModifiedSinceWithoutETag() {
        // The JMA origin sends Last-Modified only
        this.feedHeaders = Map.of("Last-Modified", List.of(LAST_MODIFIED));
        this.gateway.run();
        this.feedStatus = 304;
        this.gateway.run();
        assertThat(header(1, "If-None-Match")).isEmpty();
        assertThat(header(1, "If-Modified-Since")).contains(LAST_MODIFIED);
    }

    @Test
    void validatorsFollowLatestSuccessfulResponse() {
        this.feedHeaders = Map.of("ETag", List.of(ETAG), "Last-Modified", List.of(LAST_MODIFIED));
        this.gateway.run();
        // Upstream switched to one without ETag (e.g. the JMA origin)
        this.feedHeaders = Map.of("Last-Modified", List.of(LAST_MODIFIED));
        this.gateway.run();
        this.gateway.run();
        assertThat(header(2, "If-None-Match")).isEmpty();
        assertThat(header(2, "If-Modified-Since")).contains(LAST_MODIFIED);
    }

    @Test
    void notModifiedResponseKeepsValidators() {
        this.feedHeaders = Map.of("ETag", List.of(ETAG), "Last-Modified", List.of(LAST_MODIFIED));
        this.gateway.run();
        this.feedStatus = 304;
        this.feedHeaders = Map.of();
        this.gateway.run();
        this.gateway.run();
        assertThat(header(2, "If-None-Match")).contains(ETAG);
        assertThat(header(2, "If-Modified-Since")).contains(LAST_MODIFIED);
    }

    private java.util.Optional<String> header(int index, String name) {
        return this.feedRequests.get(index).headers().firstValue(name);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, Map<String, List<String>> headers) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>"
                .getBytes(StandardCharsets.UTF_8));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }
}
