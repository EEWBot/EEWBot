package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static net.teamfruit.eewbot.registry.destination.remote.RemoteApiFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteConfigApiClientTest {
    private RemoteApiFixture api;

    @BeforeEach void setup() throws Exception { this.api = new RemoteApiFixture(); }
    @AfterEach void close() { this.api.close(); }

    @Test void accessHeadersOnEveryEndpointAndEmptySnapshot() {
        try (var client = this.api.client()) {
            client.checkCompatibility();
            var source = new RemoteDestinationConfigSource(client);
            assertThat(source.getRevision()).isZero();
            var snapshot = source.loadSnapshot();
            assertThat(snapshot.getRevision()).isZero();
            assertThat(snapshot.size()).isZero();
        }
        assertThat(this.api.total()).isEqualTo(3);
        assertThat(this.api.badHeaders).hasValue(0);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "bad\r\nheader", "secret with spaces", "秘密"})
    void missingOrInvalidCredentialFailsBeforeNetwork(String secret) {
        assertThatThrownBy(() -> new RemoteConfigApiClient(this.api.config(), ID, secret, true))
                .isInstanceOf(RemoteConfigException.class).hasNoCause();
        assertThatThrownBy(() -> new RemoteConfigApiClient(this.api.config(), secret, SECRET, true))
                .isInstanceOf(RemoteConfigException.class).hasNoCause();
        assertThat(this.api.total()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"", "http://example.com", "https://user:secret@example.com", "https://example.com?x=secret",
            "https://example.com#secret", "https://example.com/path", "https://example.com:0", "https://example.com:99999", "https://example.com/%2e%2e", "not a URI"})
    void invalidOriginNeverReachesNetworkOrLeaksInput(String url) {
        var config = Codecs.GSON.fromJson("{\"baseUrl\": " + Codecs.GSON.toJson(url) + "}", ConfigV2.Remote.class);
        assertThatThrownBy(() -> new RemoteConfigApiClient(config, ID, SECRET))
                .isInstanceOf(RemoteConfigException.class).hasNoCause().hasMessageNotContaining("secret");
        assertThat(this.api.total()).isZero();
    }

    @Test void loopbackHttpRequiresExplicitTestAllowance() {
        assertThatThrownBy(() -> new RemoteConfigApiClient(this.api.config(), ID, SECRET))
                .isInstanceOf(RemoteConfigException.class);
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 302, 429, 503})
    void httpFailuresAreSanitizedAndDeferredWithoutRedirects(int status) {
        this.api.reply(META, status, SECRET + URL);
        try (var client = this.api.client()) {
            assertThatThrownBy(client::checkCompatibility).isInstanceOf(RemoteConfigException.class)
                    .hasNoCause().hasMessageNotContaining(SECRET).hasMessageNotContaining(URL);
            assertThatThrownBy(client::checkCompatibility).hasMessageContaining("backoff");
        }
        assertThat(this.api.count(META)).isEqualTo(1);
        assertThat(this.api.count("/redirected")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"not-json synthetic-token", "{} {}", "{\"apiVersion\":1,\"apiVersion\":2}", "[]", "null", "{\"apiVersion\":1,}"})
    void invalidJsonIsRejectedWithoutRawCause(String json) {
        this.api.reply(META, 200, json);
        try (var client = this.api.client()) {
            assertThatThrownBy(client::checkCompatibility).isInstanceOf(RemoteConfigException.class)
                    .hasNoCause().hasMessageNotContaining("synthetic-token");
        }
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"apiVersion\":1,\"capabilities\":[\"configRevision\",\"deliverySnapshot\"]}",
            "{\"apiVersion\":2,\"capabilities\":[\"configRevision\"]}",
            "{\"apiVersion\":2,\"capabilities\":[]}",
            "{\"apiVersion\":1.0,\"capabilities\":[]}",
            "{\"apiVersion\":\"1\",\"capabilities\":[]}",
            "{\"apiVersion\":2,\"capabilities\":[true]}"})
    void incompatibleProtocolFailsStartup(String json) {
        this.api.reply(META, 200, json);
        try (var client = this.api.client()) {
            assertThatThrownBy(() -> RemoteDestinationBackend.initialize(this.api.config(), client))
                    .isInstanceOf(RemoteConfigException.class);
            assertThatThrownBy(client::checkCompatibility).hasMessageContaining("closed");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void timeoutCoversHeadersAndChunkedBody(boolean slowBody) {
        this.api.replies.put(META, new Reply(200, "{}", 1000, slowBody));
        try (var client = this.api.client(this.api.config(150, 1024))) {
            long start = System.nanoTime();
            assertThatThrownBy(client::checkCompatibility).isInstanceOf(RemoteConfigException.class).hasNoCause();
            assertThat(System.nanoTime() - start).isLessThan(900_000_000L);
            assertThatThrownBy(client::checkCompatibility).hasMessageContaining("backoff");
        }
    }

    @Test void chunkedBodySizeLimitAndConfigurableSnapshotLimit() {
        String body = "{\"revision\":0,\"destinations\":[],\"padding\":\"" + "a".repeat(2000) + "\"}";
        this.api.reply(SNAPSHOT, 200, body);
        try (var client = this.api.client(this.api.config(2000, 1024))) {
            assertThatThrownBy(() -> new RemoteDestinationConfigSource(client).loadSnapshot())
                    .isInstanceOf(RemoteConfigException.class).hasNoCause();
        }
        try (var client = this.api.client(this.api.config(2000, 4096))) {
            assertThat(new RemoteDestinationConfigSource(client).loadSnapshot().size()).isZero();
        }
    }

    @Test void backendClosesClientOnDiscoveryFailure() {
        var client = mock(RemoteConfigApiClient.class);
        var failure = new RemoteConfigException("discovery failure");
        doThrow(failure).when(client).checkCompatibility();
        assertThatThrownBy(() -> RemoteDestinationBackend.initialize(this.api.config(), client)).isSameAs(failure);
        verify(client).close();
    }

    @Test void closeIsIdempotentAndShutsDownOwnedResources() {
        var client = this.api.client();
        var backend = RemoteDestinationBackend.initialize(this.api.config(), client);
        assertThat(backend.getPollIntervalMillis()).isEqualTo(20);
        backend.close();
        backend.close();
        client.close();
        assertThat(backend.getPollScheduler().isShutdown()).isTrue();
        assertThatThrownBy(backend.getConfigSource()::loadSnapshot).hasMessageContaining("closed");
    }
}
