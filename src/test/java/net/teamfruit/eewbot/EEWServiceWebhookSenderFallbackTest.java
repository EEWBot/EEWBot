package net.teamfruit.eewbot;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EEWServiceWebhookSenderFallbackTest {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123/token";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        this.executor.shutdownNow();
        Thread.interrupted();
    }

    @Test
    void sendMessageFallsBackToDirectWebhookWhenWebhookSenderIsUnreachable() throws Exception {
        FakeHttpClient httpClient = FakeHttpClient.failingSend(new IOException("connection refused"));
        EEWService service = createService(httpClient, "");

        service.sendMessage(ChannelFilter.builder().build(), testEntity());

        assertThat(httpClient.sentRequests).hasSize(1);
        assertThat(httpClient.sentRequests.get(0).uri()).isEqualTo(URI.create("http://webhook-sender.test/api/send"));
        assertThat(httpClient.asyncRequests).hasSize(1);
        assertThat(httpClient.asyncRequests.get(0).uri()).isEqualTo(URI.create(WEBHOOK_URL));
    }

    @Test
    void sendMessageFallsBackToDirectWebhookWhenWebhookSenderReturnsAbnormalStatus() {
        FakeHttpClient httpClient = FakeHttpClient.sendStatus(500, "failed");
        EEWService service = createService(httpClient, "");

        service.sendMessage(ChannelFilter.builder().build(), testEntity());

        assertThat(httpClient.sentRequests).hasSize(1);
        assertThat(httpClient.asyncRequests).hasSize(1);
        assertThat(httpClient.asyncRequests.get(0).uri()).isEqualTo(URI.create(WEBHOOK_URL));
    }

    @Test
    void sendMessageDoesNotFallbackWhenWebhookSenderReturnsSuccess() {
        FakeHttpClient httpClient = FakeHttpClient.sendStatus(204, "");
        EEWService service = createService(httpClient, "");

        service.sendMessage(ChannelFilter.builder().build(), testEntity());

        assertThat(httpClient.sentRequests).hasSize(1);
        assertThat(httpClient.asyncRequests).isEmpty();
    }

    @Test
    void sendMessageAcceptsBlankWebhookSenderCustomHeader() {
        FakeHttpClient httpClient = FakeHttpClient.sendStatus(204, "");
        EEWService service = createService(httpClient, "");

        service.sendMessage(ChannelFilter.builder().build(), testEntity());

        assertThat(httpClient.sentRequests).hasSize(1);
        assertThat(httpClient.sentRequests.get(0).headers().map()).doesNotContainKey("");
    }

    private EEWService createService(FakeHttpClient httpClient, String customHeader) {
        ConfigV2 config = new ConfigV2();
        config.getWebhookSender().setAddress("http://webhook-sender.test");
        config.getWebhookSender().setCustomHeader(customHeader);

        I18n i18n = new I18n("ja_JP");
        EmbedContext embedContext = new EmbedContext(null, null, i18n);
        DestinationAdminRegistry adminRegistry = mock(DestinationAdminRegistry.class);

        return new EEWService(
                null,
                filter -> new DeliveryPartition(
                        Map.of(123L, new DeliveryTarget(123L, "ja_jp", WEBHOOK_URL)),
                        Collections.emptyMap()
                ),
                adminRegistry,
                "https://example.com/avatar.png",
                i18n,
                embedContext,
                this.executor,
                httpClient,
                config
        );
    }

    private static Entity testEntity() {
        return new Entity() {
            @Override
            public <T> T createEmbed(String lang, EmbedContext ctx, IEmbedBuilder<T> builder) {
                return null;
            }

            @Override
            public MessageCreateSpec createMessage(String lang, EmbedContext ctx) {
                return MessageCreateSpec.builder().content("fallback").build();
            }

            @Override
            public DiscordWebhook createWebhook(String lang, EmbedContext ctx) {
                return DiscordWebhook.builder().content("webhook").build();
            }
        };
    }

    private static class FakeHttpClient extends HttpClient {

        private final AtomicReference<IOException> sendFailure;
        private final int sendStatus;
        private final String sendBody;
        private final List<HttpRequest> sentRequests = new ArrayList<>();
        private final List<HttpRequest> asyncRequests = new ArrayList<>();

        private FakeHttpClient(IOException sendFailure, int sendStatus, String sendBody) {
            this.sendFailure = new AtomicReference<>(sendFailure);
            this.sendStatus = sendStatus;
            this.sendBody = sendBody;
        }

        private static FakeHttpClient failingSend(IOException failure) {
            return new FakeHttpClient(failure, 200, "");
        }

        private static FakeHttpClient sendStatus(int statusCode, String body) {
            return new FakeHttpClient(null, statusCode, body);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            this.sentRequests.add(request);
            IOException failure = this.sendFailure.get();
            if (failure != null) {
                throw failure;
            }
            @SuppressWarnings("unchecked")
            T body = (T) this.sendBody;
            return new SimpleResponse<>(request, this.sendStatus, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.asyncRequests.add(request);
            return CompletableFuture.completedFuture(new SimpleResponse<>(request, 204, null));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException();
        }
    }

    private record SimpleResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Collections.emptyMap(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return this.request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
