package net.teamfruit.eewbot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Nested
    @DisplayName("maskUrl()")
    class MaskUrlTests {

        @Test
        @DisplayName("should mask token in Discord webhook URL but keep webhook id")
        void discordWebhookUrl() {
            String url = "https://discord.com/api/webhooks/123456789/abcdefToken";
            assertThat(LogSanitizer.maskUrl(url)).isEqualTo("https://discord.com/api/webhooks/123456789/***");
        }

        @Test
        @DisplayName("generic URL should keep scheme/host/path, mask userinfo and query values")
        void genericUrl() {
            String url = "https://user:pass@example.com:8443/api/notfounds?token=secret&limit=10";
            String masked = LogSanitizer.maskUrl(url);
            assertThat(masked).isEqualTo("https://***@example.com:8443/api/notfounds?token=***&limit=***");
        }

        @Test
        @DisplayName("generic URL without userinfo/query should be unchanged apart from normalization")
        void genericUrlNoSecrets() {
            String url = "https://example.com/api/status";
            assertThat(LogSanitizer.maskUrl(url)).isEqualTo(url);
        }

        @Test
        @DisplayName("non-URL string should be returned as-is")
        void notAUrl() {
            assertThat(LogSanitizer.maskUrl("not a url")).isEqualTo("not a url");
        }

        @Test
        @DisplayName("null should return null")
        void nullUrl() {
            assertThat(LogSanitizer.maskUrl(null)).isNull();
        }
    }

    @Nested
    @DisplayName("maskDiscordWebhookUrlsInText()")
    class MaskDiscordWebhookUrlsInTextTests {

        @Test
        @DisplayName("should mask multiple webhook URLs in text, keeping ids and surrounding text")
        void multipleUrls() {
            String text = "sent to https://discord.com/api/webhooks/111/tokenA and https://discordapp.com/api/webhooks/222/tokenB ok";
            String masked = LogSanitizer.maskDiscordWebhookUrlsInText(text);
            assertThat(masked).isEqualTo(
                    "sent to https://discord.com/api/webhooks/111/*** and https://discordapp.com/api/webhooks/222/*** ok");
        }

        @Test
        @DisplayName("text without webhook URLs should be unchanged")
        void noUrls() {
            String text = "status=200 body={\"ok\":true}";
            assertThat(LogSanitizer.maskDiscordWebhookUrlsInText(text)).isEqualTo(text);
        }

        @Test
        @DisplayName("null should return null")
        void nullText() {
            assertThat(LogSanitizer.maskDiscordWebhookUrlsInText(null)).isNull();
        }
    }

    @Nested
    @DisplayName("safeExceptionMessage()")
    class SafeExceptionMessageTests {

        @Test
        @DisplayName("should mask webhook URL embedded in exception message")
        void messageWithWebhookUrl() {
            Exception e = new RuntimeException("failed to connect to https://discord.com/api/webhooks/999/secretToken");
            assertThat(LogSanitizer.safeExceptionMessage(e))
                    .isEqualTo("failed to connect to https://discord.com/api/webhooks/999/***");
        }

        @Test
        @DisplayName("null message should fall back to exception class name")
        void nullMessage() {
            Exception e = new RuntimeException();
            assertThat(LogSanitizer.safeExceptionMessage(e)).isEqualTo(RuntimeException.class.getName());
        }

        @Test
        @DisplayName("null throwable should return null")
        void nullThrowable() {
            assertThat(LogSanitizer.safeExceptionMessage(null)).isNull();
        }
    }
}
