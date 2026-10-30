package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.Codecs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Every test request stays on a bound loopback server, including redirect/error tests. */
final class RemoteApiFixture implements AutoCloseable {
    static final String META = "/internal/v2/meta";
    static final String REVISION = "/internal/v2/config/revision";
    static final String SNAPSHOT = "/internal/v2/config/snapshot";
    static final String ID = "synthetic-client-id";
    static final String SECRET = "synthetic-client-secret";
    static final String WEBHOOK_ID = "1234567890123456792";
    static final String TOKEN = "synthetic-token-xxxxxxxxxxxx";
    static final String URL = "https://discord.com/api/webhooks/" + WEBHOOK_ID + "/" + TOKEN + "?thread_id=1234567890123456791";
    static final long TARGET = 1234567890123456791L;
    static final ObjectMapper JSON = new ObjectMapper();
    final HttpServer server;
    final ExecutorService executor = Executors.newCachedThreadPool();
    final Map<String, Reply> replies = new ConcurrentHashMap<>();
    final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    final AtomicInteger badHeaders = new AtomicInteger();
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger maxActive = new AtomicInteger();

    RemoteApiFixture() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.setExecutor(this.executor);
        this.server.createContext("/", this::respond);
        reply(META, 200, "{\"apiVersion\":2,\"capabilities\":[\"configRevision\",\"deliverySnapshot\"]}");
        reply(REVISION, 200, "{\"revision\":0}");
        reply(SNAPSHOT, 200, "{\"revision\":0,\"destinations\":[]}");
        this.server.start();
    }

    String origin() { return "http://127.0.0.1:" + this.server.getAddress().getPort(); }

    ConfigV2.Remote config() { return config(2000, 32 * 1024 * 1024); }
    ConfigV2.Remote config(long timeout, int limit) {
        return Codecs.GSON.fromJson("{\"baseUrl\":\"" + origin() + "\",\"pollIntervalMillis\":20,"
                + "\"requestTimeoutMillis\":" + timeout + ",\"maxSnapshotBytes\":" + limit + "}", ConfigV2.Remote.class);
    }

    RemoteConfigApiClient client() { return client(config()); }
    RemoteConfigApiClient client(ConfigV2.Remote config) { return new RemoteConfigApiClient(config, ID, SECRET, true); }
    void reply(String path, int status, String body) { this.replies.put(path, new Reply(status, body, 0, false)); }
    int count(String path) { return this.counts.computeIfAbsent(path, key -> new AtomicInteger()).get(); }
    int total() { return this.counts.values().stream().mapToInt(AtomicInteger::get).sum(); }

    private void respond(HttpExchange exchange) throws IOException {
        int concurrent = this.active.incrementAndGet();
        this.maxActive.accumulateAndGet(concurrent, Math::max);
        this.counts.computeIfAbsent(exchange.getRequestURI().getPath(), key -> new AtomicInteger()).incrementAndGet();
        if (!ID.equals(exchange.getRequestHeaders().getFirst("CF-Access-Client-Id"))
                || !SECRET.equals(exchange.getRequestHeaders().getFirst("CF-Access-Client-Secret"))
                || !"GET".equals(exchange.getRequestMethod()) || exchange.getRequestURI().getRawQuery() != null) {
            this.badHeaders.incrementAndGet();
        }
        Reply reply = this.replies.getOrDefault(exchange.getRequestURI().getPath(), new Reply(404, "{}", 0, false));
        try (exchange) {
            if (!reply.slowBody()) pause(reply.delayMillis());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            if (reply.status() == 302) exchange.getResponseHeaders().set("Location", origin() + "/redirected");
            exchange.sendResponseHeaders(reply.status(), 0); // chunked: size limit must work without Content-Length
            if (reply.slowBody()) {
                exchange.getResponseBody().write(' ');
                exchange.getResponseBody().flush();
                pause(reply.delayMillis());
            }
            exchange.getResponseBody().write(reply.body().getBytes(StandardCharsets.UTF_8));
        } finally {
            this.active.decrementAndGet();
        }
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static ObjectNode destination() {
        ObjectNode d = JSON.createObjectNode();
        d.put("targetId", Long.toString(TARGET));
        d.put("channelId", "1234567890123456790");
        d.put("threadId", Long.toString(TARGET));
        d.put("guildId", "1234567890123456789");
        d.put("eewAlert", true);
        d.put("eewPrediction", false);
        d.put("eewDecimation", true);
        d.put("quakeInfo", false);
        d.put("tsunami", true);
        d.put("minIntensity", 4);
        d.put("lang", "ja_jp");
        ObjectNode webhook = d.putObject("webhook");
        webhook.put("id", WEBHOOK_ID);
        webhook.put("token", TOKEN);
        return d;
    }

    void snapshot(long revision, ObjectNode... destinations) {
        ObjectNode root = JSON.createObjectNode();
        root.put("revision", revision);
        var array = root.putArray("destinations");
        for (ObjectNode d : destinations) array.add(d);
        reply(SNAPSHOT, 200, root.toString());
    }

    @Override public void close() {
        this.server.stop(0);
        this.executor.shutdownNow();
    }

    record Reply(int status, String body, long delayMillis, boolean slowBody) { }
}
