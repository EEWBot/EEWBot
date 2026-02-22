package net.teamfruit.eewbot.registry.destination.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelDeserializerTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
                .registerTypeAdapter(Channel.class, new ChannelDeserializer())
                .registerTypeAdapter(ChannelWebhook.class, new ChannelWebhookDeserializer())
                .create();
    }

    @Nested
    @DisplayName("New format deserialization")
    class NewFormatTests {

        @Test
        @DisplayName("should deserialize new format with channelId and threadId")
        void newFormatWithThread() {
            String json = """
                    {
                      "guildId": 100,
                      "channelId": 200,
                      "threadId": 300,
                      "eewAlert": true,
                      "eewPrediction": false,
                      "eewDecimation": true,
                      "quakeInfo": false,
                      "minIntensity": 3,
                      "lang": "en_us"
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);

            assertThat(channel.getGuildId()).isEqualTo(100L);
            assertThat(channel.getChannelId()).isEqualTo(200L);
            assertThat(channel.getThreadId()).isEqualTo(300L);
            assertThat(channel.isEewAlert()).isTrue();
            assertThat(channel.isEewPrediction()).isFalse();
            assertThat(channel.isEewDecimation()).isTrue();
            assertThat(channel.isQuakeInfo()).isFalse();
            assertThat(channel.getMinIntensity()).isEqualTo(SeismicIntensity.THREE);
            assertThat(channel.getLang()).isEqualTo("en_us");
            assertThat(channel.getWebhook()).isNull();
        }

        @Test
        @DisplayName("should deserialize new format with channelId only (no thread)")
        void newFormatNoThread() {
            String json = """
                    {
                      "channelId": 500,
                      "eewAlert": true,
                      "quakeInfo": true
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);

            assertThat(channel.getChannelId()).isEqualTo(500L);
            assertThat(channel.getThreadId()).isNull();
        }
    }

    @Nested
    @DisplayName("Old format migration")
    class OldFormatTests {

        @Test
        @DisplayName("should extract threadId from webhook.threadId in old format")
        void oldFormatWithWebhookThreadId() {
            String json = """
                    {
                      "guildId": 100,
                      "eewAlert": true,
                      "webhook": {
                        "id": 999,
                        "token": "tok",
                        "threadId": 777
                      }
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);

            // Old format: channelId is null (set by migration code later)
            assertThat(channel.getChannelId()).isNull();
            assertThat(channel.getThreadId()).isEqualTo(777L);
            assertThat(channel.getWebhook()).isNotNull();
            assertThat(channel.getWebhook().id()).isEqualTo(999L);
        }

        @Test
        @DisplayName("should set channelId=null and threadId=null for old format without webhook.threadId")
        void oldFormatWithoutWebhookThreadId() {
            String json = """
                    {
                      "guildId": 100,
                      "eewAlert": true,
                      "webhook": {
                        "id": 888,
                        "token": "tok"
                      }
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);

            assertThat(channel.getChannelId()).isNull();
            assertThat(channel.getThreadId()).isNull();
        }

        @Test
        @DisplayName("should set channelId=null and threadId=null for old format without webhook")
        void oldFormatWithoutWebhook() {
            String json = """
                    {
                      "guildId": 100,
                      "eewAlert": false
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);

            assertThat(channel.getChannelId()).isNull();
            assertThat(channel.getThreadId()).isNull();
            assertThat(channel.getWebhook()).isNull();
        }
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValueTests {

        @Test
        @DisplayName("missing lang should default to ja_jp")
        void missingLangDefaultsToJaJp() {
            String json = """
                    {
                      "channelId": 1,
                      "eewAlert": true
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.getLang()).isEqualTo("ja_jp");
        }

        @Test
        @DisplayName("null lang should default to ja_jp")
        void nullLangDefaultsToJaJp() {
            String json = """
                    {
                      "channelId": 1,
                      "lang": null
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.getLang()).isEqualTo("ja_jp");
        }

        @Test
        @DisplayName("missing minIntensity should default to ONE")
        void missingMinIntensityDefaultsToOne() {
            String json = """
                    {
                      "channelId": 1
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.getMinIntensity()).isEqualTo(SeismicIntensity.ONE);
        }

        @Test
        @DisplayName("null minIntensity should default to ONE")
        void nullMinIntensityDefaultsToOne() {
            String json = """
                    {
                      "channelId": 1,
                      "minIntensity": null
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.getMinIntensity()).isEqualTo(SeismicIntensity.ONE);
        }

        @Test
        @DisplayName("missing boolean fields should default to false")
        void missingBooleanFieldsDefaultToFalse() {
            String json = """
                    {
                      "channelId": 1
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.isEewAlert()).isFalse();
            assertThat(channel.isEewPrediction()).isFalse();
            assertThat(channel.isEewDecimation()).isFalse();
            assertThat(channel.isQuakeInfo()).isFalse();
        }
    }

    @Nested
    @DisplayName("Webhook deserialization")
    class WebhookTests {

        @Test
        @DisplayName("should deserialize new webhook format with url")
        void newWebhookFormat() {
            String json = """
                    {
                      "channelId": 1,
                      "webhook": {
                        "url": "https://discord.com/api/webhooks/111/token123"
                      }
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.getWebhook()).isNotNull();
            assertThat(channel.getWebhook().url()).isEqualTo("https://discord.com/api/webhooks/111/token123");
        }

        @Test
        @DisplayName("should deserialize old webhook format with id/token/threadId")
        void oldWebhookFormat() {
            String json = """
                    {
                      "channelId": 1,
                      "webhook": {
                        "id": 222,
                        "token": "secretToken",
                        "threadId": 333
                      }
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.getWebhook()).isNotNull();
            assertThat(channel.getWebhook().id()).isEqualTo(222L);
            assertThat(channel.getWebhook().token()).isEqualTo("secretToken");
            assertThat(channel.getWebhook().url()).contains("thread_id=333");
        }

        @Test
        @DisplayName("null webhook should result in null")
        void nullWebhook() {
            String json = """
                    {
                      "channelId": 1,
                      "webhook": null
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.getWebhook()).isNull();
        }
    }

    @Nested
    @DisplayName("GuildId handling")
    class GuildIdTests {

        @Test
        @DisplayName("present guildId should make isGuild() true")
        void presentGuildId() {
            String json = """
                    {
                      "guildId": 100,
                      "channelId": 1
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.isGuild()).isTrue();
            assertThat(channel.getGuildId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("null guildId should make isGuild() false")
        void nullGuildId() {
            String json = """
                    {
                      "guildId": null,
                      "channelId": 1
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.isGuild()).isFalse();
            assertThat(channel.getGuildId()).isNull();
        }

        @Test
        @DisplayName("missing guildId should make isGuild() false")
        void missingGuildId() {
            String json = """
                    {
                      "channelId": 1
                    }
                    """;
            Channel channel = gson.fromJson(json, Channel.class);
            assertThat(channel.isGuild()).isFalse();
        }
    }
}
