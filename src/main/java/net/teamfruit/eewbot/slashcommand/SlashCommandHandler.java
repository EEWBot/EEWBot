package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.spec.InteractionCallbackSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.rest.service.ApplicationService;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.discord.ImageRefreshButton;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.impl.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.core.Disposables;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static net.teamfruit.eewbot.slashcommand.SlashCommandUtils.*;

/** Owns the complete local Interactions registration and subscription lifecycle. */
public class SlashCommandHandler implements Disposable {

    private static final String SHUTDOWN_MESSAGE = "Bot is shutting down. Please try again later.";

    private final Disposable.Composite subscriptions = Disposables.composite();

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        registerCommand(new SetupSlashCommand());
        registerCommand(new QuakeInfoSlashCommand());
        registerCommand(new InviteSlashCommand());
        registerCommand(new TestMessageSlashCommand());
        registerCommand(new LangSlashCommand());
        registerCommand(new HelpSlashCommand());
        registerCommand(new UnregisterSlashCommand());
    }

    public SlashCommandHandler(SlashCommandContext ctx) {
        try {
            start(ctx);
        } catch (RuntimeException | Error e) {
            try {
                this.subscriptions.dispose();
            } catch (RuntimeException | Error disposeFailure) {
                e.addSuppressed(disposeFailure);
            }
            throw e;
        }
    }

    private void start(SlashCommandContext ctx) {
        ApplicationService service = ctx.client().getRestClient().getApplicationService();

        this.subscriptions.add(service.getGlobalApplicationCommands(ctx.applicationId())
                .filter(data -> !commands.containsKey(data.name()))
                .flatMap(data -> service.deleteGlobalApplicationCommand(ctx.applicationId(), data.id().asLong()))
                .subscribe());
        commands.values().forEach(command -> this.subscriptions.add(
                service.createGlobalApplicationCommand(ctx.applicationId(), command.buildCommand()).subscribe()));

        this.subscriptions.add(ctx.client().on(ApplicationCommandInteractionEvent.class)
                .flatMap(event -> unlessShuttingDown(ctx, event))
                .flatMap(event -> Mono.just(event.getCommandName())
                        .filter(name -> commands.containsKey(name))
                        .map(commands::get)
                        .flatMap(cmd -> deferIfRequested(event, cmd, cmd.isDefer(), cmd.isEphemeralWhenDefer()))
                        .flatMap(cmd -> unlessShuttingDownAfterDefer(ctx, event, cmd, cmd.isDefer()))
                        .flatMap(cmd -> {
                            Channel channel = ctx.adminRegistry().get(event.getInteraction().getChannelId().asLong());
                            return cmd.on(ctx, event, channel, channel != null ? channel.getLang() : ctx.config().getBase().getDefaultLanguage());
                        })
                        .doOnError(err -> Log.logger.error("Error during {} command", event.getCommandName(), err))
                        .onErrorResume(err -> replyOrFollowUp(event, commands.get(event.getCommandName()).isDefer(),
                                createErrorEmbed(getLanguage(ctx, event), ctx)
                                        .title("eewbot.scmd.error")
                                        .description(ExceptionUtils.getMessage(err))
                                        .toEmbedCreateSpec()
                        )))
                .onErrorResume(e -> {
                    Log.logger.error("Unhandled exception during ApplicationCommandInteractionEvent handling", e);
                    return Mono.empty();
                })
                .repeat()
                .subscribe());

        this.subscriptions.add(ctx.client().on(SelectMenuInteractionEvent.class)
                .flatMap(event -> unlessShuttingDown(ctx, event))
                .flatMap(event -> Mono.justOrEmpty(findComponentCommand(ISelectMenuSlashCommand.class,
                                cmd -> cmd.getCustomIds().contains(event.getCustomId())))
                        .flatMap(cmd -> deferIfRequested(event, cmd, cmd.isDeferOnSelect(), cmd.isEphemeralOnSelectWhenDefer()))
                        .flatMap(cmd -> unlessShuttingDownAfterDefer(ctx, event, cmd, cmd.isDeferOnSelect()))
                        .flatMap(cmd -> cmd.onSelect(ctx, event, getLanguage(ctx, event)))
                        .doOnError(err -> Log.logger.error("Error during {} action", event.getCustomId(), err))
                        .onErrorResume(err -> Mono.empty()))
                .onErrorResume(e -> {
                    Log.logger.error("Unhandled exception during SelectMenuInteractionEvent handling", e);
                    return Mono.empty();
                })
                .repeat()
                .subscribe());

        this.subscriptions.add(ctx.client().on(ButtonInteractionEvent.class)
                .flatMap(event -> unlessShuttingDown(ctx, event))
                .flatMap(event -> {
                    if (ImageRefreshButton.CUSTOM_ID.equals(event.getCustomId()))
                        return event.deferEdit(InteractionCallbackSpec.builder().build())
                                .then(Mono.defer(() -> event.editReply(InteractionReplyEditSpec.builder()
                                        .components(List.of()).build()).then()));
                    return Mono.justOrEmpty(findComponentCommand(IButtonSlashCommand.class,
                                    cmd -> cmd.getCustomIds().contains(event.getCustomId())))
                            .flatMap(cmd -> deferIfRequested(event, cmd, cmd.isDeferOnClick(), cmd.isEphemeralOnClickWhenDefer()))
                            .flatMap(cmd -> unlessShuttingDownAfterDefer(ctx, event, cmd, cmd.isDeferOnClick()))
                            .flatMap(cmd -> cmd.onClick(ctx, event, getLanguage(ctx, event)))
                            .doOnError(err -> Log.logger.error("Error during {} action", event.getCustomId(), err))
                            .onErrorResume(err -> Mono.empty());
                })
                .onErrorResume(e -> {
                    Log.logger.error("Unhandled exception during ButtonInteractionEvent handling", e);
                    return Mono.empty();
                })
                .repeat()
                .subscribe());
    }

    /** Before anything is acknowledged: refuse with an ephemeral reply and drop the event. */
    private static <E extends DeferrableInteractionEvent> Mono<E> unlessShuttingDown(SlashCommandContext ctx, E event) {
        if (!ctx.shutdownFlag().get())
            return Mono.just(event);
        return event.reply(SHUTDOWN_MESSAGE).withEphemeral(true).then(Mono.empty());
    }

    private static <C> Mono<C> deferIfRequested(DeferrableInteractionEvent event, C command, boolean defer, boolean ephemeral) {
        if (!defer)
            return Mono.just(command);
        return event.deferReply(InteractionCallbackSpec.builder().ephemeral(ephemeral).build()).thenReturn(command);
    }

    /** Shutdown may begin while deferring; a deferred interaction can only be answered by follow-up. */
    private static <C> Mono<C> unlessShuttingDownAfterDefer(SlashCommandContext ctx, DeferrableInteractionEvent event, C command, boolean deferred) {
        if (!ctx.shutdownFlag().get())
            return Mono.just(command);
        Mono<?> notice = deferred
                ? event.createFollowup(SHUTDOWN_MESSAGE)
                : event.reply(SHUTDOWN_MESSAGE).withEphemeral(true);
        return notice.then(Mono.empty());
    }

    private static <C> Optional<C> findComponentCommand(Class<C> type, Predicate<C> matches) {
        return commands.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(matches)
                .findAny();
    }

    @Override
    public void dispose() {
        this.subscriptions.dispose();
    }

    @Override
    public boolean isDisposed() {
        return this.subscriptions.isDisposed();
    }

    private static void registerCommand(ISlashCommand command) {
        commands.put(command.getCommandName(), command);
    }

}
