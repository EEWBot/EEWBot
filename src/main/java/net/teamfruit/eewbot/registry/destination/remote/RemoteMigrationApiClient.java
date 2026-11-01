package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.teamfruit.eewbot.Log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-origin transport for the one-off legacy import. Unlike cleanup, an import chunk is an idempotent
 * upsert, so ambiguous failures (5xx, 429, transport, invalid response) are retried with backoff.
 */
public final class RemoteMigrationApiClient implements AutoCloseable {
    static final String PATH = "/internal/v2/migration/destinations";
    static final int MAX_CHUNK = 500;
    private final URI origin;
    private final String clientId;
    private final String clientSecret;
    private final long timeoutMillis;
    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public RemoteMigrationApiClient(String baseUrl, String clientId, String clientSecret, long connectTimeoutMillis, long requestTimeoutMillis) {
        this(baseUrl, clientId, clientSecret, connectTimeoutMillis, requestTimeoutMillis, 8, 1000, false);
    }

    // Package-private: loopback HTTP and short backoff for local tests only.
    RemoteMigrationApiClient(String baseUrl, String clientId, String clientSecret, long connectTimeoutMillis,
                             long requestTimeoutMillis, int maxAttempts, long baseBackoffMillis, boolean allowLoopbackHttp) {
        if (connectTimeoutMillis <= 0 || connectTimeoutMillis > 60000 || requestTimeoutMillis <= 0
                || requestTimeoutMillis > 300000 || maxAttempts < 1 || baseBackoffMillis < 0) {
            throw new RemoteConfigException("Invalid remote migration limits");
        }
        this.origin = RemoteConfigApiClient.validateOrigin(baseUrl, allowLoopbackHttp);
        this.clientId = credential(clientId);
        this.clientSecret = credential(clientSecret);
        this.timeoutMillis = requestTimeoutMillis;
        this.maxAttempts = maxAttempts;
        this.baseBackoffMillis = baseBackoffMillis;
        this.mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(8192).build())
                .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static String credential(String value) {
        if (value == null || value.isBlank() || !value.chars().allMatch(c -> c >= 33 && c <= 126)) {
            throw new RemoteConfigException("Migration requires valid EEWBOT_CF_ACCESS_CLIENT_ID and EEWBOT_CF_ACCESS_CLIENT_SECRET");
        }
        return value;
    }

    /**
     * Upserts one chunk and returns the number of rows the Worker changed.
     * Throws immediately on a non-retryable 4xx; retries everything else up to maxAttempts.
     */
    public int importDestinations(List<Map<String, Object>> destinations) {
        if (destinations.isEmpty() || destinations.size() > MAX_CHUNK) {
            throw new RemoteConfigException("Migration chunk must hold 1-" + MAX_CHUNK + " destinations");
        }
        byte[] body;
        try {
            body = this.mapper.writeValueAsBytes(Map.of("destinations", destinations));
        } catch (Exception e) {
            throw new RemoteConfigException("Unable to encode migration chunk");
        }
        for (int attempt = 1; ; attempt++) {
            String failure;
            try {
                return post(body);
            } catch (Retryable e) {
                failure = e.getMessage();
            }
            if (attempt >= this.maxAttempts) {
                throw new RemoteConfigException("Remote migration failed after " + attempt + " attempts (last: " + failure + ")");
            }
            long delay = backoffMillis(attempt);
            Log.logger.warn("Remote migration attempt {}/{} failed ({}); retrying in {} ms", attempt, this.maxAttempts, failure, delay);
            try {
                TimeUnit.MILLISECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RemoteConfigException("Remote migration interrupted; resending the chunk is safe");
            }
        }
    }

    private int post(byte[] body) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(this.origin.resolve(PATH))
                    .timeout(Duration.ofMillis(this.timeoutMillis))
                    .header("CF-Access-Client-Id", this.clientId).header("CF-Access-Client-Secret", this.clientSecret)
                    .header("Accept", "application/json").header("Cache-Control", "no-store")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            pending = this.http.sendAsync(request, info ->
                    new RemoteConfigApiClient.LimitedBodySubscriber(info.statusCode() == 200 ? 64 * 1024 : 0));
            HttpResponse<byte[]> response = pending.get(this.timeoutMillis, TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status == 429 || status >= 500) throw new Retryable("status " + status);
            if (status != 200) {
                // 400 invalid_request, 401/403 Access, 404 Worker without the import route: retrying cannot help.
                throw new RemoteConfigException("Remote migration HTTP request rejected (status " + status + "); stopped");
            }
            JsonNode result = this.mapper.readTree(response.body());
            JsonNode affected = result == null ? null : result.get("affected");
            if (result == null || !result.isObject() || result.size() != 1 || affected == null || !affected.isIntegralNumber()
                    || !affected.canConvertToInt() || affected.intValue() < 0) {
                throw new Retryable("invalid response");
            }
            return affected.intValue();
        } catch (RemoteConfigException | Retryable e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteConfigException("Remote migration interrupted; resending the chunk is safe");
        } catch (Exception e) {
            // Parser, TLS and HTTP exceptions may embed response data or credentials.
            throw new Retryable("timeout, transport, response size or JSON");
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private long backoffMillis(int attempt) {
        long cap = Math.min(30000, this.baseBackoffMillis << Math.min(attempt - 1, 5));
        return cap <= 1 ? cap : ThreadLocalRandom.current().nextLong(cap / 2, cap + 1);
    }

    @Override
    public void close() {
        this.http.shutdownNow();
    }

    private static final class Retryable extends RuntimeException {
        Retryable(String message) { super(message, null, false, false); }
    }
}
