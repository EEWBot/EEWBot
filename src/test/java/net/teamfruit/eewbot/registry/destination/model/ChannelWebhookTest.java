package net.teamfruit.eewbot.registry.destination.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelWebhookTest {

    private static final String BASE_URL = "https://discord.com/api/webhooks/";

    @Nested
    @DisplayName("id()")
    class IdTests {

        @Test
        @DisplayName("should extract webhook ID from standard URL")
        void standardUrl() {
            ChannelWebhook webhook = new ChannelWebhook(BASE_URL + "123456789/abcdefToken");
            assertThat(webhook.id()).isEqualTo(123456789L);
        }

        @Test
        @DisplayName("should extract webhook ID from URL with thread_id query parameter")
        void urlWithThreadId() {
            ChannelWebhook webhook = new ChannelWebhook(BASE_URL + "987654321/tokenValue?thread_id=111222333");
            assertThat(webhook.id()).isEqualTo(987654321L);
        }

        @Test
        @DisplayName("should handle large webhook IDs (snowflake)")
        void largeSnowflakeId() {
            long snowflake = 1234567890123456789L;
            ChannelWebhook webhook = new ChannelWebhook(BASE_URL + snowflake + "/someToken");
            assertThat(webhook.id()).isEqualTo(snowflake);
        }
    }

    @Nested
    @DisplayName("token()")
    class TokenTests {

        @Test
        @DisplayName("should extract token from standard URL")
        void standardUrl() {
            ChannelWebhook webhook = new ChannelWebhook(BASE_URL + "123/mySecretToken");
            assertThat(webhook.token()).isEqualTo("mySecretToken");
        }

        @Test
        @DisplayName("should strip thread_id query parameter from token")
        void urlWithThreadId() {
            ChannelWebhook webhook = new ChannelWebhook(BASE_URL + "123/myToken?thread_id=456");
            assertThat(webhook.token()).isEqualTo("myToken");
        }

        @Test
        @DisplayName("should handle token with special characters before query")
        void tokenWithDashes() {
            ChannelWebhook webhook = new ChannelWebhook(BASE_URL + "123/a-b_c.d");
            assertThat(webhook.token()).isEqualTo("a-b_c.d");
        }
    }

    @Nested
    @DisplayName("of() factory")
    class OfTests {

        @Test
        @DisplayName("of(id, token) should create URL without thread_id")
        void ofIdAndToken() {
            ChannelWebhook webhook = ChannelWebhook.of(123L, "tok");
            assertThat(webhook.url()).isEqualTo(BASE_URL + "123/tok");
            assertThat(webhook.id()).isEqualTo(123L);
            assertThat(webhook.token()).isEqualTo("tok");
        }

        @Test
        @DisplayName("of(id, token, threadId) should append thread_id query param")
        void ofWithThreadId() {
            ChannelWebhook webhook = ChannelWebhook.of(100L, "secret", 999L);
            assertThat(webhook.url()).isEqualTo(BASE_URL + "100/secret?thread_id=999");
            assertThat(webhook.id()).isEqualTo(100L);
            assertThat(webhook.token()).isEqualTo("secret");
        }

        @Test
        @DisplayName("of(id, token, null) should create URL without thread_id")
        void ofWithNullThreadId() {
            ChannelWebhook webhook = ChannelWebhook.of(100L, "secret", null);
            assertThat(webhook.url()).isEqualTo(BASE_URL + "100/secret");
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class RoundtripTests {

        @Test
        @DisplayName("id and token should roundtrip through of()")
        void roundtrip() {
            long id = 555666777888L;
            String token = "xyzToken123";
            ChannelWebhook webhook = ChannelWebhook.of(id, token);

            assertThat(webhook.id()).isEqualTo(id);
            assertThat(webhook.token()).isEqualTo(token);
        }

        @Test
        @DisplayName("id and token should roundtrip through of() with thread_id")
        void roundtripWithThread() {
            long id = 111222333L;
            String token = "abc";
            Long threadId = 444555666L;
            ChannelWebhook webhook = ChannelWebhook.of(id, token, threadId);

            assertThat(webhook.id()).isEqualTo(id);
            assertThat(webhook.token()).isEqualTo(token);
            // thread_id is in query param, not extracted by id()/token()
            assertThat(webhook.url()).contains("thread_id=" + threadId);
        }
    }

    @Test
    @DisplayName("getUrl() should return the original URL")
    void getUrl() {
        String url = BASE_URL + "123/token";
        ChannelWebhook webhook = new ChannelWebhook(url);
        assertThat(webhook.getUrl()).isEqualTo(url);
    }
}
