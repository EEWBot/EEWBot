package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.teamfruit.eewbot.registry.config.ConfigV2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fixed-origin writer transport. A POST is attempted once, including ambiguous failures. */
public final class RemoteCleanupApiClient implements AutoCloseable {
    private static final String PREFIX = "/internal/v1/cleanup/";
    private static final Set<String> OPERATIONS = Set.of("guild", "destinations", "webhooks", "webhook-ids");
    private final URI origin;
    private final String clientId;
    private final String clientSecret;
    private final long timeoutMillis;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final AtomicBoolean closed = new AtomicBoolean();
    private long nextAttempt = System.nanoTime();
    private int failures;

    public RemoteCleanupApiClient(ConfigV2.Remote config, String clientId, String clientSecret) {
        this(config, clientId, clientSecret, false);
    }

    RemoteCleanupApiClient(ConfigV2.Remote config, String clientId, String clientSecret, boolean allowLoopbackHttp) {
        if (config == null || config.getConnectTimeoutMillis() <= 0 || config.getConnectTimeoutMillis() > 60000
                || config.getRequestTimeoutMillis() <= 0 || config.getRequestTimeoutMillis() > 60000) {
            throw new RemoteConfigException("Invalid remote cleanup limits");
        }
        this.origin = RemoteConfigApiClient.validateOrigin(config.getBaseUrl(), allowLoopbackHttp);
        this.clientId = credential(clientId);
        this.clientSecret = credential(clientSecret);
        this.timeoutMillis = config.getRequestTimeoutMillis();
        this.mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(8192).build())
                .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static String credential(String value) {
        if (value == null || value.isBlank() || !value.chars().allMatch(c -> c >= 33 && c <= 126)) {
            throw new RemoteConfigException("Cleanup requires valid EEWBOT_CF_ACCESS_CLIENT_ID and EEWBOT_CF_ACCESS_CLIENT_SECRET");
        }
        return value;
    }

    public void checkCompatibility() {
        JsonNode meta = request("meta", null);
        JsonNode version = meta.get("apiVersion");
        JsonNode capabilities = meta.get("capabilities");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1
                || capabilities == null || !capabilities.isArray()) {
            throw new RemoteConfigException("Incompatible remote cleanup API");
        }
        boolean supported = false;
        for (JsonNode capability : capabilities) {
            if (!capability.isTextual()) throw new RemoteConfigException("Invalid cleanup capabilities");
            supported |= "destinationCleanup".equals(capability.textValue());
        }
        if (!supported) throw new RemoteConfigException("Remote API lacks destinationCleanup capability");
    }

    int mutate(String operation, Object body) {
        if (!OPERATIONS.contains(operation) || body == null) throw new RemoteConfigException("Invalid cleanup operation");
        JsonNode result = request(operation, body);
        JsonNode affected = result.get("affected");
        if (result.size() != 1 || affected == null || !affected.isIntegralNumber() || !affected.canConvertToInt()
                || affected.intValue() < 0) throw new RemoteConfigException("Invalid cleanup response");
        return affected.intValue();
    }

    // Only the cleanup worker calls this during runtime. One in-flight request, including sync count calls.
    private synchronized JsonNode request(String operation, Object body) {
        if (this.closed.get()) throw new RemoteConfigException("Remote cleanup client is closed");
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            long delay = this.nextAttempt - System.nanoTime();
            if (delay > 0) TimeUnit.NANOSECONDS.sleep(delay);
            if (this.closed.get()) throw new RemoteConfigException("Remote cleanup client is closed");
            HttpRequest.Builder builder = HttpRequest.newBuilder(this.origin.resolve(PREFIX + operation))
                    .timeout(Duration.ofMillis(this.timeoutMillis))
                    .header("CF-Access-Client-Id", this.clientId).header("CF-Access-Client-Secret", this.clientSecret)
                    .header("Accept", "application/json").header("Cache-Control", "no-store");
            if (body == null) builder.GET();
            else {
                byte[] bytes = this.mapper.writeValueAsBytes(body);
                if (bytes.length > 128 * 1024) throw new RemoteConfigException("Cleanup body size exceeded");
                builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
            }
            pending = this.http.sendAsync(builder.build(), info ->
                    new RemoteConfigApiClient.LimitedBodySubscriber(info.statusCode() == 200 ? 64 * 1024 : 0));
            HttpResponse<byte[]> response = pending.get(this.timeoutMillis, TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status != 200) {
                defer(status == 401 || status == 403 ? 60000 : backoffMillis());
                throw new RemoteConfigException("Remote cleanup HTTP request rejected (status " + status + "); operation not retried");
            }
            JsonNode result = this.mapper.readTree(response.body());
            if (result == null || !result.isObject()) throw new IllegalArgumentException();
            this.failures = 0;
            this.nextAttempt = System.nanoTime();
            return result;
        } catch (RemoteConfigException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteConfigException("Remote cleanup interrupted; outcome may be unknown; operation not retried");
        } catch (Exception e) {
            defer(backoffMillis());
            throw new RemoteConfigException("Remote cleanup failed (timeout, transport, response size or JSON); outcome may be unknown; operation not retried");
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private long backoffMillis() {
        long cap = Math.min(60000, 1000L << Math.min(this.failures++, 6));
        return ThreadLocalRandom.current().nextLong(Math.max(1, cap / 2), cap + 1);
    }

    private void defer(long millis) { this.nextAttempt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis); }

    @Override public void close() {
        if (this.closed.compareAndSet(false, true)) this.http.shutdownNow();
    }
}
