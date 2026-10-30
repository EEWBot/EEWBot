package net.teamfruit.eewbot.quakeinfo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.teamfruit.eewbot.registry.config.ConfigV2;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/** Access credentials exist only in memory. Transport/parser exceptions are discarded. */
public final class RemoteQuakeInfoSnapshotClient implements QuakeInfoSnapshotClient {
    private static final String PREFIX = "/internal/v2/quakeinfo";
    private static final Set<String> SAFE_CODES = Set.of("publisher_fenced", "snapshot_conflict", "sequence_conflict",
            "message_conflict", "snapshot_not_found", "incomplete_stale_or_fenced", "capacity_exceeded");
    private final HttpClient http;
    private final URI origin;
    private final String clientId;
    private final String clientSecret;
    private final long timeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RemoteQuakeInfoSnapshotClient(ConfigV2.QuakeInfoPublisher config) {
        this(config, System.getenv("EEWBOT_CF_ACCESS_CLIENT_ID"), System.getenv("EEWBOT_CF_ACCESS_CLIENT_SECRET"), false);
    }

    // Only package-local tests may opt into a literal loopback HTTP origin.
    RemoteQuakeInfoSnapshotClient(ConfigV2.QuakeInfoPublisher config, String clientId, String clientSecret, boolean loopback) {
        try {
            URI uri = URI.create(config.getBaseUrl());
            boolean local = loopback && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
            if (!("https".equals(uri.getScheme()) || local) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")) || uri.getPort() == 0 || uri.getPort() > 65535
                    || config.getConnectTimeoutMillis() < 1 || config.getConnectTimeoutMillis() > 60000
                    || config.getRequestTimeoutMillis() < 1 || config.getRequestTimeoutMillis() > 60000)
                throw new IllegalArgumentException();
            this.origin = uri.resolve("/");
            this.clientId = credential(clientId);
            this.clientSecret = credential(clientSecret);
            this.timeoutMillis = config.getRequestTimeoutMillis();
            this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.getConnectTimeoutMillis()))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid QuakeInfo publisher origin, timeouts or Access environment"); }
    }

    private static String credential(String value) {
        if (value == null || value.isBlank() || value.length() > 8192 || value.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException();
        return value;
    }

    private static String id(String value) {
        if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Invalid snapshot ID");
        return value;
    }

    @Override public long lease(String publisherId) {
        JsonObject body = new JsonObject(); body.addProperty("publisherId", id(publisherId));
        try {
            long epoch = request("/publisher/lease", "POST", body.toString()).get("publisherEpoch").getAsLong();
            if (epoch < 1 || epoch > 9007199254740991L) throw new Failure(0, "invalid_response");
            return epoch;
        } catch (Failure e) { throw e; }
        catch (RuntimeException e) { throw new Failure(0, "invalid_response"); }
    }
    @Override public void create(QuakeInfoSnapshot snapshot) { request("/snapshots", "POST", snapshot.metadataJson()); }
    @Override public void put(String snapshotId, String lang, int index, String body) {
        if (!QuakeInfoSnapshot.LANGUAGES.contains(lang) || index < 0 || index >= 16) throw new IllegalArgumentException("Invalid snapshot message");
        request("/snapshots/" + id(snapshotId) + "/messages/" + lang + "/" + index, "PUT", body);
    }
    @Override public void commit(String snapshotId) { request("/snapshots/" + id(snapshotId) + "/commit", "POST", "{}"); }
    @Override public Latest latest() {
        try {
            JsonObject result = request("/latest/meta", "GET", null);
            if (result.get("snapshot").isJsonNull()) return new Latest(null, null, 0);
            JsonObject meta = result.getAsJsonObject("snapshot");
            return new Latest(meta.get("snapshotId").getAsString(), meta.get("contentHash").getAsString(), meta.get("publisherEpoch").getAsLong());
        } catch (Failure e) { throw e; }
        catch (RuntimeException e) { throw new Failure(0, "invalid_response"); }
    }

    private JsonObject request(String path, String method, String body) {
        if (this.closed.get()) throw new Failure(0, "closed");
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(this.origin.resolve(PREFIX + path))
                    .timeout(Duration.ofMillis(this.timeoutMillis))
                    .header("CF-Access-Client-Id", this.clientId).header("CF-Access-Client-Secret", this.clientSecret)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
            future = this.http.sendAsync(request, info -> new LimitedBody());
            HttpResponse<byte[]> response = future.get(this.timeoutMillis, TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status != 200) {
                String code = "request_rejected";
                try {
                    // v2 envelope: {"error":{"code":"..."}}
                    String supplied = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8))
                            .getAsJsonObject().getAsJsonObject("error").get("code").getAsString();
                    if (SAFE_CODES.contains(supplied)) code = supplied;
                } catch (RuntimeException ignored) { }
                throw new Failure(status, code);
            }
            return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Failure e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new Failure(0, "interrupted"); }
        catch (Exception e) { throw new Failure(0, "transport_or_invalid_response"); }
        finally { if (future != null && !future.isDone()) future.cancel(true); }
    }

    @Override public void close() {
        if (this.closed.compareAndSet(false, true)) this.http.shutdownNow();
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return this.result; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                if ((long) this.bytes.size() + chunk.remaining() > 32 * 1024) {
                    this.subscription.cancel(); this.result.completeExceptionally(new IllegalStateException("Response too large")); return;
                }
                byte[] data = new byte[chunk.remaining()]; chunk.get(data); this.bytes.writeBytes(data);
            }
            this.subscription.request(1);
        }
        @Override public void onError(Throwable error) { this.result.completeExceptionally(new IllegalStateException("Response failed")); }
        @Override public void onComplete() { this.result.complete(this.bytes.toByteArray()); }
    }
}
