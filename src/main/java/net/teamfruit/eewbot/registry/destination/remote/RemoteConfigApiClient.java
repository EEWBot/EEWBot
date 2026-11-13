package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.teamfruit.eewbot.registry.config.ConfigV2;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Flow;

/** Fixed-origin GET transport. No cookies, redirects, persistent cache, or raw error causes. */
public final class RemoteConfigApiClient implements AutoCloseable {
    private static final int SMALL_RESPONSE_BYTES = 64 * 1024;
    private final URI origin;
    private final String clientId;
    private final String clientSecret;
    private final ConfigV2.Remote config;
    private final ExecutorService executor;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Map<String, RetryState> retries = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public RemoteConfigApiClient(ConfigV2.Remote config, String clientId, String clientSecret) {
        this(config, clientId, clientSecret, false);
    }

    // Package-private explicit loopback allowance for local HTTP tests only.
    RemoteConfigApiClient(ConfigV2.Remote config, String clientId, String clientSecret, boolean allowLoopbackHttp) {
        if (config == null || config.getPollIntervalMillis() <= 0 || config.getPollIntervalMillis() > 3600000
                || config.getConnectTimeoutMillis() <= 0 || config.getConnectTimeoutMillis() > 60000
                || config.getRequestTimeoutMillis() <= 0 || config.getRequestTimeoutMillis() > 60000
                || config.getMaxSnapshotBytes() <= 0) {
            throw new RemoteConfigException("Invalid remote configuration limits");
        }
        this.origin = validateOrigin(config.getBaseUrl(), allowLoopbackHttp);
        this.clientId = validateCredential(clientId);
        this.clientSecret = validateCredential(clientSecret);
        this.config = config;
        this.mapper = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(8192).build())
                .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.executor = Executors.newFixedThreadPool(2, r -> new Thread(r, "eewbot-remote-http"));
        try {
            this.http = HttpClient.newBuilder().executor(this.executor)
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMillis()))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        } catch (RuntimeException | Error e) {
            this.executor.shutdownNow();
            throw new RemoteConfigException("Unable to initialize remote HTTP client");
        }
    }

    static URI validateOrigin(String value, boolean allowLoopbackHttp) {
        try {
            URI uri = URI.create(value);
            boolean loopback = "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost());
            if (!("https".equals(uri.getScheme()) || allowLoopbackHttp && loopback && "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
            return uri.resolve("/");
        } catch (RuntimeException e) {
            throw new RemoteConfigException("Remote baseUrl must be an explicit HTTPS origin without userinfo, path, query or fragment");
        }
    }

    private static String validateCredential(String value) {
        if (value == null || value.isBlank() || !value.chars().allMatch(c -> c >= 33 && c <= 126)) {
            throw new RemoteConfigException("Remote mode requires valid EEWBOT_CF_ACCESS_CLIENT_ID and EEWBOT_CF_ACCESS_CLIENT_SECRET");
        }
        return value;
    }

    JsonNode revision() { return get("/internal/v2/config/revision", SMALL_RESPONSE_BYTES); }
    JsonNode snapshot() { return get("/internal/v2/config/snapshot", this.config.getMaxSnapshotBytes()); }

    // At most one request in flight across polling, reload and convergence checks.
    private synchronized JsonNode get(String path, int limit) {
        if (this.closed.get()) throw new RemoteConfigException("Remote configuration client is closed");
        RetryState state = this.retries.computeIfAbsent(path, key -> new RetryState());
        if (System.nanoTime() - state.nextAttempt < 0) {
            throw new RemoteConfigException("Remote configuration retry deferred by backoff");
        }
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(this.origin.resolve(path))
                    .timeout(Duration.ofMillis(this.config.getRequestTimeoutMillis()))
                    .header("CF-Access-Client-Id", this.clientId)
                    .header("CF-Access-Client-Secret", this.clientSecret)
                    .header("Accept", "application/json").header("Cache-Control", "no-store").GET().build();
            pending = this.http.sendAsync(request, info -> new LimitedBodySubscriber(info.statusCode() == 200 ? limit : 0));
            // Covers a slow/chunked response body as well as the response headers.
            HttpResponse<byte[]> response = pending.get(this.config.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                state.defer(60000);
                throw new RemoteConfigException("Remote configuration authentication denied; check Service Token expiry/revocation and restart after updating environment");
            }
            if (status != 200) {
                state.fail();
                throw new RemoteConfigException("Remote configuration HTTP request rejected (status " + status + ")");
            }
            JsonNode result = this.mapper.readTree(response.body());
            if (result == null || !result.isObject()) throw new IllegalArgumentException();
            state.reset();
            return result;
        } catch (RemoteConfigException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.fail();
            throw new RemoteConfigException("Remote configuration request interrupted");
        } catch (Exception e) {
            state.fail();
            // Parser, TLS and HTTP exceptions may embed response data or credentials.
            throw new RemoteConfigException("Remote configuration request failed (timeout, transport, size limit or invalid JSON)");
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.http.shutdownNow();
            this.executor.shutdownNow();
        }
    }

    private static final class RetryState {
        private int failures;
        private long nextAttempt = System.nanoTime();
        void reset() { this.failures = 0; this.nextAttempt = System.nanoTime(); }
        void defer(long millis) { this.nextAttempt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis); }
        void fail() {
            long cap = Math.min(60000, 1000L << Math.min(this.failures++, 6));
            defer(ThreadLocalRandom.current().nextLong(Math.max(1, cap / 2), cap + 1));
        }
    }

    /** Cancels before retaining more than limit bytes, even for chunked responses. */
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBodySubscriber(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return this.body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (this.limit == 0) {
                subscription.cancel();
                this.body.complete(new byte[0]);
            } else subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                if (item.remaining() > this.limit - this.bytes.size()) {
                    this.subscription.cancel();
                    this.body.completeExceptionally(new RemoteConfigException("Remote response size limit exceeded"));
                    return;
                }
                byte[] part = new byte[item.remaining()];
                item.get(part);
                this.bytes.writeBytes(part);
            }
            this.subscription.request(1);
        }
        @Override public void onError(Throwable error) { this.body.completeExceptionally(error); }
        @Override public void onComplete() { this.body.complete(this.bytes.toByteArray()); }
    }
}
