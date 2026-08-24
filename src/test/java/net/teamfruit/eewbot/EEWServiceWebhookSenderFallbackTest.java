package net.teamfruit.eewbot;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.discord.IEmbedBuilder;
import net.teamfruit.eewbot.entity.discord.PendingEmbed;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EEWServiceWebhookSenderFallbackTest {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123/token";
    private static final String PAGE_1 = "page-one";
    private static final String PAGE_2 = "page-two";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        this.executor.shutdownNow();
        Thread.interrupted();
    }

    @Test
    void sendMessageFallsBackToDirectWebhookWhenWebhookSenderIsUnreachable() {
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

    @Test
    void sendMessageResumesFromTheFailedChunkWhenWebhookSenderFailsMidway() throws Exception {
        FakeHttpClient httpClient = FakeHttpClient.sendStatuses(204, 500);
        EEWService service = createService(httpClient, "");

        service.sendMessage(ChannelFilter.builder().build(), twoPageEntity());

        assertThat(httpClient.sentRequests).hasSize(2);
        assertThat(httpClient.asyncRequests).hasSize(1);
        assertThat(bodyOf(httpClient.asyncRequests.get(0))).contains(PAGE_2).doesNotContain(PAGE_1);
    }

    @Test
    void sendMessageResendsEveryPageWhenWebhookSenderFailsOnTheFirstChunk() throws Exception {
        FakeHttpClient httpClient = FakeHttpClient.sendStatuses(500);
        EEWService service = createService(httpClient, "");

        service.sendMessage(ChannelFilter.builder().build(), twoPageEntity());

        assertThat(httpClient.sentRequests).hasSize(1);
        assertThat(httpClient.asyncRequests).hasSize(2);
        assertThat(bodyOf(httpClient.asyncRequests.get(0))).contains(PAGE_1);
        assertThat(bodyOf(httpClient.asyncRequests.get(1))).contains(PAGE_2);
    }

    @Test
    void sendMessageResumesFromTheFailedChunkWhenDirectWebhookFailsMidway() {
        FakeHttpClient httpClient = FakeHttpClient.sendStatus(204, "").withAsyncStatuses(204, 500);
        EEWService service = spy(createService(httpClient, "", ""));
        doReturn(Mono.empty()).when(service).directSendMessagePassErrors(anyLong(), any());

        service.sendMessage(ChannelFilter.builder().build(), twoPageEntity());

        assertThat(httpClient.sentRequests).isEmpty();
        assertThat(httpClient.asyncRequests).hasSize(2);
        verify(service, times(1)).directSendMessagePassErrors(eq(123L), any());
        verify(service).directSendMessagePassErrors(123L, messageSpec(PAGE_2));
    }

    private EEWService createService(FakeHttpClient httpClient, String customHeader) {
        return createService(httpClient, customHeader, "http://webhook-sender.test");
    }

    private EEWService createService(FakeHttpClient httpClient, String customHeader, String senderAddress) {
        ConfigV2 config = new ConfigV2();
        config.getWebhookSender().setAddress(senderAddress);
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
            public List<PendingEmbed> createEmbeds(String lang, EmbedContext ctx, Supplier<IEmbedBuilder> factory) {
                return List.of();
            }

            @Override
            public List<MessageCreateSpec> createMessages(String lang, EmbedContext ctx) {
                return List.of(MessageCreateSpec.builder().content("fallback").build());
            }

            @Override
            public List<DiscordWebhook> createWebhooks(String lang, EmbedContext ctx) {
                return List.of(DiscordWebhook.builder().content("webhook").build());
            }
        };
    }

    /**
     * An entity that does not fit in one message and is therefore split into two pages.
     */
    private static Entity twoPageEntity() {
        return new Entity() {
            @Override
            public List<PendingEmbed> createEmbeds(String lang, EmbedContext ctx, Supplier<IEmbedBuilder> factory) {
                return List.of();
            }

            @Override
            public List<MessageCreateSpec> createMessages(String lang, EmbedContext ctx) {
                return List.of(messageSpec(PAGE_1), messageSpec(PAGE_2));
            }

            @Override
            public List<DiscordWebhook> createWebhooks(String lang, EmbedContext ctx) {
                return List.of(
                        DiscordWebhook.builder().content(PAGE_1).build(),
                        DiscordWebhook.builder().content(PAGE_2).build()
                );
            }
        };
    }

    private static MessageCreateSpec messageSpec(String content) {
        return MessageCreateSpec.builder().content(content).build();
    }

    private static String bodyOf(HttpRequest request) throws InterruptedException {
        StringBuilder body = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                body.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        return body.toString();
    }

    private static class FakeHttpClient extends HttpClient {

        private final AtomicReference<IOException> sendFailure;
        /**
         * Status per {@code send} call; the last entry repeats for any further calls.
         */
        private final int[] sendStatuses;
        private final String sendBody;
        /**
         * Status per {@code sendAsync} call; the last entry repeats for any further calls.
         */
        private int[] asyncStatuses = {204};
        private final List<HttpRequest> sentRequests = new ArrayList<>();
        private final List<HttpRequest> asyncRequests = new ArrayList<>();

        private FakeHttpClient(IOException sendFailure, int[] sendStatuses, String sendBody) {
            this.sendFailure = new AtomicReference<>(sendFailure);
            this.sendStatuses = sendStatuses;
            this.sendBody = sendBody;
        }

        private static FakeHttpClient failingSend(IOException failure) {
            return new FakeHttpClient(failure, new int[]{200}, "");
        }

        private static FakeHttpClient sendStatus(int statusCode, String body) {
            return new FakeHttpClient(null, new int[]{statusCode}, body);
        }

        private static FakeHttpClient sendStatuses(int... statuses) {
            return new FakeHttpClient(null, statuses, "");
        }

        private FakeHttpClient withAsyncStatuses(int... statuses) {
            this.asyncStatuses = statuses;
            return this;
        }

        private static int statusAt(int[] statuses, int callIndex) {
            return statuses[Math.min(callIndex, statuses.length - 1)];
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
            int callIndex = this.sentRequests.size();
            this.sentRequests.add(request);
            IOException failure = this.sendFailure.get();
            if (failure != null) {
                throw failure;
            }
            @SuppressWarnings("unchecked")
            T body = (T) this.sendBody;
            return new SimpleResponse<>(request, statusAt(this.sendStatuses, callIndex), body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            int callIndex = this.asyncRequests.size();
            this.asyncRequests.add(request);
            return CompletableFuture.completedFuture(new SimpleResponse<>(request, statusAt(this.asyncStatuses, callIndex), null));
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
