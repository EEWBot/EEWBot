package net.teamfruit.eewbot.slashcommand;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.rest.service.ApplicationService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlashCommandHandlerLifecycleTest {

    @Test
    void disposeStopsCommandRegistrationAndAllInteractionSubscriptionsExactlyOnce() {
        GatewayDiscordClient gateway = mock(GatewayDiscordClient.class, RETURNS_DEEP_STUBS);
        ApplicationService service = gateway.getRestClient().getApplicationService();
        AtomicInteger cancelled = new AtomicInteger();
        when(service.getGlobalApplicationCommands(0)).thenReturn(Flux.<discord4j.discordjson.json.ApplicationCommandData>never()
                .doOnCancel(cancelled::incrementAndGet));
        when(service.createGlobalApplicationCommand(eq(0L), any())).thenAnswer(call -> Mono.never().doOnCancel(cancelled::incrementAndGet));
        when(gateway.on(ApplicationCommandInteractionEvent.class)).thenReturn(Flux.<ApplicationCommandInteractionEvent>never()
                .doOnCancel(cancelled::incrementAndGet));
        when(gateway.on(SelectMenuInteractionEvent.class)).thenReturn(Flux.<SelectMenuInteractionEvent>never()
                .doOnCancel(cancelled::incrementAndGet));
        when(gateway.on(ButtonInteractionEvent.class)).thenReturn(Flux.<ButtonInteractionEvent>never()
                .doOnCancel(cancelled::incrementAndGet));
        SlashCommandContext context = mock(SlashCommandContext.class);
        when(context.client()).thenReturn(gateway);

        SlashCommandHandler handler = new SlashCommandHandler(context);
        assertThat(handler.isDisposed()).isFalse();
        assertThat(cancelled.get()).isZero();

        handler.dispose();
        handler.dispose();

        assertThat(handler.isDisposed()).isTrue();
        assertThat(cancelled.get()).isEqualTo(SlashCommandHandler.commands.size() + 4);
    }

    @Test
    void partialStartupFailureDisposesAlreadyStartedSubscriptions() {
        GatewayDiscordClient gateway = mock(GatewayDiscordClient.class, RETURNS_DEEP_STUBS);
        ApplicationService service = gateway.getRestClient().getApplicationService();
        AtomicInteger cancelled = new AtomicInteger();
        when(service.getGlobalApplicationCommands(0)).thenReturn(Flux.<discord4j.discordjson.json.ApplicationCommandData>never()
                .doOnCancel(cancelled::incrementAndGet));
        when(service.createGlobalApplicationCommand(eq(0L), any())).thenReturn(Mono.never());
        when(gateway.on(ApplicationCommandInteractionEvent.class)).thenReturn(Flux.<ApplicationCommandInteractionEvent>never()
                .doOnCancel(cancelled::incrementAndGet));
        IllegalStateException failure = new IllegalStateException("select listener unavailable");
        when(gateway.on(SelectMenuInteractionEvent.class)).thenThrow(failure);
        SlashCommandContext context = mock(SlashCommandContext.class);
        when(context.client()).thenReturn(gateway);

        assertThatThrownBy(() -> new SlashCommandHandler(context)).isSameAs(failure);

        assertThat(cancelled.get()).isEqualTo(2);
        verify(gateway, never()).on(ButtonInteractionEvent.class);
    }
}
