package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionFollowupCreateMono;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.discordjson.json.MessageCreateRequest;
import discord4j.discordjson.json.MessageData;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.service.ChannelService;
import discord4j.rest.util.MultipartRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestMessageSlashCommandTest {

    private static final long CHANNEL_ID = 1234567890123456789L;
    private static final String LANGUAGE = "ja_jp";
    private static final I18n I18N = new I18n(LANGUAGE);

    @Mock(answer = RETURNS_DEEP_STUBS)
    private GatewayDiscordClient gateway;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ApplicationCommandInteractionEvent event;
    @Mock
    private DestinationAdminRegistry admin;
    @Mock
    private HttpClient http;
    @Captor
    private ArgumentCaptor<MultipartRequest<MessageCreateRequest>> messageRequest;
    @Captor
    private ArgumentCaptor<InteractionFollowupCreateSpec> followup;

    private ChannelService channels;
    private SlashCommandContext context;
    private final TestMessageSlashCommand command = new TestMessageSlashCommand();

    @BeforeEach
    void setUp() {
        this.channels = this.gateway.getRestClient().getChannelService();
        when(this.event.getInteraction().getChannelId()).thenReturn(Snowflake.of(CHANNEL_ID));
        when(this.event.createFollowup(any(InteractionFollowupCreateSpec.class)))
                .thenReturn(Mono.just(mock(Message.class)));
        ConfigV2 config = new ConfigV2();
        // Even with a sender configured, a destination without a webhook uses Discord REST.
        config.getWebhookSender().setAddress("http://sender.test");
        this.context = new SlashCommandContext(this.admin, I18N, config, this.gateway, this.http,
                "EEWBot", "https://example.com/avatar.png", null, null, 1L, new AtomicBoolean());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void noWebhookSendsToInteractionChannelAndRepliesWithSuccess(boolean registered) {
        when(this.channels.createMessage(eq(CHANNEL_ID), any())).thenReturn(Mono.just(mock(MessageData.class)));
        when(this.event.createFollowup(anyString())).thenAnswer(call ->
                InteractionFollowupCreateMono.of(this.event).withContent(call.getArgument(0, String.class)));
        Channel channel = registered ? Channel.createDefault(null, CHANNEL_ID, null, LANGUAGE) : null;

        Mono<Void> action = this.command.on(this.context, this.event, channel, LANGUAGE);
        verify(this.channels, never()).createMessage(anyLong(), any());
        StepVerifier.create(action).verifyComplete();

        verify(this.channels).createMessage(eq(CHANNEL_ID), this.messageRequest.capture());
        assertThat(this.messageRequest.getValue().getFiles()).isEmpty();
        var embeds = this.messageRequest.getValue().getJsonPayload().embeds().get();
        assertThat(embeds).hasSize(1);
        assertThat(embeds.get(0).title().get()).isEqualTo(I18N.get(LANGUAGE, "eewbot.scmd.testmessage.title"));
        assertThat(embeds.get(0).description().get()).isEqualTo(I18N.get(LANGUAGE, "eewbot.scmd.testmessage.nowebhook"));
        verify(this.event).createFollowup(I18N.get(LANGUAGE, "eewbot.scmd.testmessage.success.nowebhook"));
        verify(this.event).createFollowup(this.followup.capture());
        assertThat(this.followup.getValue().content().get())
                .isEqualTo(I18N.get(LANGUAGE, "eewbot.scmd.testmessage.success.nowebhook"));
        assertThat(this.followup.getValue().embeds()).isEmpty();
        verifyNoMoreInteractions(this.channels);
        verifyNoInteractions(this.admin, this.http);
    }

    @ParameterizedTest
    @CsvSource({"403,missingperms", "429,ratelimit", "404,unknown", "500,unknown"})
    void discordRestErrorsReplyWithTheCorrespondingErrorEmbed(int status, String messageKey) {
        ClientException failure = mock(ClientException.class);
        when(failure.getStatus()).thenReturn(HttpResponseStatus.valueOf(status));
        if (messageKey.equals("unknown")) when(failure.getMessage()).thenReturn("Discord REST failure");
        when(this.channels.createMessage(eq(CHANNEL_ID), any())).thenReturn(Mono.error(failure));

        StepVerifier.create(this.command.on(this.context, this.event, null, LANGUAGE)).verifyComplete();

        assertErrorReply(messageKey, messageKey.equals("unknown") ? "Discord REST failure" : null);
    }

    @Test
    void transportFailureRepliesWithAnUnknownErrorEmbed() {
        when(this.channels.createMessage(eq(CHANNEL_ID), any()))
                .thenReturn(Mono.error(new IOException("connection reset")));

        StepVerifier.create(this.command.on(this.context, this.event, null, LANGUAGE)).verifyComplete();

        assertErrorReply("unknown", "connection reset");
    }

    private void assertErrorReply(String messageKey, String detail) {
        verify(this.channels).createMessage(eq(CHANNEL_ID), any());
        verifyNoMoreInteractions(this.channels);
        verify(this.event, never()).createFollowup(anyString());
        verify(this.event).createFollowup(this.followup.capture());
        assertThat(this.followup.getValue().embeds()).hasSize(1);
        var embed = this.followup.getValue().embeds().get(0).asRequest();
        assertThat(embed.title().get()).isEqualTo(I18N.get(LANGUAGE, "eewbot.scmd.testmessage.error.title"));
        String description = I18N.get(LANGUAGE, "eewbot.scmd.testmessage.error." + messageKey);
        if (detail == null) {
            assertThat(embed.description().get()).isEqualTo(description);
        } else {
            assertThat(embed.description().get()).startsWith(description.substring(0, description.indexOf("%s"))).contains(detail);
        }
        verifyNoInteractions(this.admin, this.http);
    }
}
