package net.teamfruit.eewbot.quakeinfo;

import com.sun.net.httpserver.HttpServer;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class RemoteQuakeInfoSnapshotClientTest {
    private static final QuakeInfoSnapshot SNAPSHOT = new QuakeInfoSnapshot("{\"eventId\":\"synthetic\"}", "synthetic-fingerprint");
    private static ConfigV2.QuakeInfoPublisher config(String origin, long timeout) {
        var json = new com.google.gson.JsonObject(); json.addProperty("baseUrl",origin); json.addProperty("requestTimeoutMillis",timeout);
        return Codecs.GSON.fromJson(json,ConfigV2.QuakeInfoPublisher.class);
    }
    @Test void sendsExistingCommonAccessHeadersAndExactJson() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var calls = new AtomicInteger();
        server.createContext("/internal/v2/quakeinfo/latest",exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
            assertThat(exchange.getRequestHeaders().getFirst("CF-Access-Client-Id")).isEqualTo("synthetic-id");
            assertThat(exchange.getRequestHeaders().getFirst("CF-Access-Client-Secret")).isEqualTo("synthetic-secret");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/json");
            assertThat(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).isEqualTo(SNAPSHOT.body());
            byte[] body = "{\"accepted\":false,\"revision\":7}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); calls.incrementAndGet();
        });
        server.start();
        try (var client = new RemoteQuakeInfoSnapshotClient(config("http://127.0.0.1:" + server.getAddress().getPort(),1000),"synthetic-id","synthetic-secret",true)) {
            assertThat(client.publish(SNAPSHOT)).isEqualTo(new QuakeInfoSnapshotClient.Result(false,7));
            assertThat(calls).hasValue(1);
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(ints = {302,307,403,409,500})
    void rejectsRedirectsAndSanitizesFailures(int status) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var redirected = new AtomicInteger();
        server.createContext("/internal/v2/quakeinfo/latest",exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Location","/secret-redirect");
            byte[] body = "{\"error\":\"SQL secret-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/secret-redirect",exchange -> { redirected.incrementAndGet(); exchange.close(); });
        server.start();
        try (var client = new RemoteQuakeInfoSnapshotClient(config("http://127.0.0.1:" + server.getAddress().getPort(),1000),"synthetic-id","synthetic-secret",true)) {
            assertThatThrownBy(() -> client.publish(SNAPSHOT)).isInstanceOf(QuakeInfoSnapshotClient.Failure.class)
                    .hasMessageNotContaining("secret-token").hasMessageNotContaining("synthetic-secret").hasMessageNotContaining("SQL");
            assertThat(redirected).hasValue(0);
        } finally { server.stop(0); }
    }
    @ParameterizedTest @CsvSource(delimiter = '|', value = {
            "400|{\"error\":{\"code\":\"invalid_quakeinfo\"}}|invalid_quakeinfo",
            "503|{\"error\":{\"code\":\"storage_unavailable\"}}|storage_unavailable",
            "500|{\"error\":{\"code\":\"internal_error\"}}|internal_error",
            "409|{\"error\":{\"code\":\"publisher_fenced\"}}|request_rejected",
            "400|{\"error\":\"invalid_quakeinfo\"}|request_rejected",
            "400|{\"error\":{\"code\":{}}}|request_rejected",
            "400|{\"error\":{}}|request_rejected"})
    void readsOnlyAllowlistedV2ErrorCodes(int status, String json, String expected) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v2/quakeinfo/latest",exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (var client = new RemoteQuakeInfoSnapshotClient(config("http://127.0.0.1:" + server.getAddress().getPort(),1000),"synthetic-id","synthetic-secret",true)) {
            assertThatThrownBy(() -> client.publish(SNAPSHOT)).isInstanceOfSatisfying(QuakeInfoSnapshotClient.Failure.class,
                    failure -> { assertThat(failure.status()).isEqualTo(status); assertThat(failure.code()).isEqualTo(expected); });
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(booleans = {true,false})
    void boundsSlowBodiesAndOversizedResponses(boolean slow) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
        server.createContext("/internal/v2/quakeinfo/latest",exchange -> {
            try {
                exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(200,0);
                if (slow) Thread.sleep(1500);
                exchange.getResponseBody().write("x".repeat(40000).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { } finally { exchange.close(); }
        });
        server.start();
        try (var client = new RemoteQuakeInfoSnapshotClient(config("http://127.0.0.1:" + server.getAddress().getPort(),150),"synthetic-id","synthetic-secret",true)) {
            assertThatThrownBy(() -> client.publish(SNAPSHOT)).isInstanceOf(QuakeInfoSnapshotClient.Failure.class);
        } finally { server.stop(0); executor.shutdownNow(); }
    }
    @Test void publicConstructorRejectsHttpAndUrlCredentialsEvenWithPublisherEnabled() {
        for (String origin : new String[] {"http://127.0.0.1", "https://user:password@example.com", "https://example.com/path", "https://example.com?token=secret"})
            assertThatThrownBy(() -> new RemoteQuakeInfoSnapshotClient(config(origin,1000))).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("password").hasMessageNotContaining("token=secret");
    }
}
