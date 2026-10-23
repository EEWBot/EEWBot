package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.spec.InteractionCallbackSpec;
import discord4j.rest.service.ApplicationService;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.impl.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.core.Disposables;

import java.util.HashMap;

import static net.teamfruit.eewbot.slashcommand.SlashCommandUtils.*;

/** Owns the complete local Interactions registration and subscription lifecycle. */
public class SlashCommandHandler implements Disposable {

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
                .flatMap(event -> {
                    if (ctx.shutdownFlag().get()) {
                        return event.reply("Bot is shutting down. Please try again later.")
                                .withEphemeral(true)
                                .then(Mono.empty());
                    }
                    return Mono.just(event);
                })
                .flatMap(event -> Mono.just(event.getCommandName())
                        .filter(name -> commands.containsKey(name))
                        .map(commands::get)
                        .flatMap(cmd -> cmd.isDefer() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralWhenDefer())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> {
                            if (ctx.shutdownFlag().get()) {
                                if (cmd.isDefer()) {
                                    return event.createFollowup("Bot is shutting down. Please try again later.")
                                            .then(Mono.empty());
                                }
                                return event.reply("Bot is shutting down. Please try again later.")
                                        .withEphemeral(true)
                                        .then(Mono.empty());
                            }
                            return Mono.just(cmd);
                        })
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
                .flatMap(event -> {
                    if (ctx.shutdownFlag().get()) {
                        return event.reply("Bot is shutting down. Please try again later.")
                                .withEphemeral(true)
                                .then(Mono.empty());
                    }
                    return Mono.just(event);
                })
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(ISelectMenuSlashCommand.class::isInstance)
                                .map(ISelectMenuSlashCommand.class::cast)
                                .filter(cmd -> cmd.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(cmd -> cmd.isDeferOnSelect() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralOnSelectWhenDefer())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> {
                            if (ctx.shutdownFlag().get()) {
                                if (cmd.isDeferOnSelect()) {
                                    return event.createFollowup("Bot is shutting down. Please try again later.")
                                            .then(Mono.empty());
                                }
                                return event.reply("Bot is shutting down. Please try again later.")
                                        .withEphemeral(true)
                                        .then(Mono.empty());
                            }
                            return Mono.just(cmd);
                        })
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
                .flatMap(event -> {
                    if (ctx.shutdownFlag().get()) {
                        return event.reply("Bot is shutting down. Please try again later.")
                                .withEphemeral(true)
                                .then(Mono.empty());
                    }
                    return Mono.just(event);
                })
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(IButtonSlashCommand.class::isInstance)
                                .map(IButtonSlashCommand.class::cast)
                                .filter(cmd -> cmd.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(cmd -> cmd.isDeferOnClick() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralOnClickWhenDefer())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> {
                            if (ctx.shutdownFlag().get()) {
                                if (cmd.isDeferOnClick()) {
                                    return event.createFollowup("Bot is shutting down. Please try again later.")
                                            .then(Mono.empty());
                                }
                                return event.reply("Bot is shutting down. Please try again later.")
                                        .withEphemeral(true)
                                        .then(Mono.empty());
                            }
                            return Mono.just(cmd);
                        })
                        .flatMap(cmd -> cmd.onClick(ctx, event, getLanguage(ctx, event)))
                        .doOnError(err -> Log.logger.error("Error during {} action", event.getCustomId(), err))
                        .onErrorResume(err -> Mono.empty()))
                .onErrorResume(e -> {
                    Log.logger.error("Unhandled exception during ButtonInteractionEvent handling", e);
                    return Mono.empty();
                })
                .repeat()
                .subscribe());
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
