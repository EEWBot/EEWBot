package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.spec.InteractionCallbackSpec;
import discord4j.rest.service.ApplicationService;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.channel.Channel;
import net.teamfruit.eewbot.slashcommand.impl.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;

import static net.teamfruit.eewbot.slashcommand.SlashCommandUtils.*;

public class SlashCommandHandler {

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        registerCommand(new SetupSlashCommand());
        registerCommand(new QuakeInfoSlashCommand());
        registerCommand(new TimeSlashCommand());
        registerCommand(new InviteSlashCommand());
        registerCommand(new TestMessageSlashCommand());
        registerCommand(new LangSlashCommand());
        registerCommand(new HelpSlashCommand());
    }

    public SlashCommandHandler(EEWBot bot) {
        ApplicationService service = bot.getClient().getRestClient().getApplicationService();

        service.getGlobalApplicationCommands(bot.getApplicationId())
                .filter(data -> !commands.containsKey(data.name()))
                .flatMap(data -> service.deleteGlobalApplicationCommand(bot.getApplicationId(), data.id().asLong()))
                .subscribe();
        commands.values().forEach(command -> service.createGlobalApplicationCommand(bot.getApplicationId(), command.buildCommand()).subscribe());

        bot.getClient().on(ApplicationCommandInteractionEvent.class)
                .flatMap(event -> Mono.just(event.getCommandName())
                        .filter(name -> commands.containsKey(name))
                        .map(commands::get)
                        .flatMap(cmd -> cmd.isDefer() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralWhenDefer())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> {
                            Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
                            return cmd.on(bot, event, channel, channel != null ? channel.getLang() : bot.getConfig().getBase().getDefaultLanguage());
                        })
                        .doOnError(err -> Log.logger.error("Error during {} command", event.getCommandName(), err))
                        .onErrorResume(err -> replyOrFollowUp(event, commands.get(event.getCommandName()).isDefer(),
                                createErrorEmbed(getLanguage(bot, event))
                                        .title("eewbot.scmd.error")
                                        .description(ExceptionUtils.getMessage(err))
                                        .build()
                        )))
                .onErrorResume(e -> {
                    Log.logger.error("Unhandled exception during ApplicationCommandInteractionEvent handling", e);
                    return Mono.empty();
                })
                .repeat()
                .subscribe();

        bot.getClient().on(SelectMenuInteractionEvent.class)
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(ISelectMenuSlashCommand.class::isInstance)
                                .map(ISelectMenuSlashCommand.class::cast)
                                .filter(cmd -> cmd.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(cmd -> cmd.isDeferOnSelect() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralOnSelectWhenDefer())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> cmd.onSelect(bot, event, getLanguage(bot, event)))
                        .doOnError(err -> Log.logger.error("Error during {} action", event.getCustomId(), err))
                        .onErrorResume(err -> Mono.empty()))
                .onErrorResume(e -> {
                    Log.logger.error("Unhandled exception during SelectMenuInteractionEvent handling", e);
                    return Mono.empty();
                })
                .repeat()
                .subscribe();

        bot.getClient().on(ButtonInteractionEvent.class)
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(IButtonSlashCommand.class::isInstance)
                                .map(IButtonSlashCommand.class::cast)
                                .filter(cmd -> cmd.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(cmd -> cmd.isDeferOnClick() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralOnClickWhenDefer())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> cmd.onClick(bot, event, getLanguage(bot, event)))
                        .doOnError(err -> Log.logger.error("Error during {} action", event.getCustomId(), err))
                        .onErrorResume(err -> Mono.empty()))
                .onErrorResume(e -> {
                    Log.logger.error("Unhandled exception during ButtonInteractionEvent handling", e);
                    return Mono.empty();
                })
                .repeat()
                .subscribe();
    }

    private static void registerCommand(ISlashCommand command) {
        commands.put(command.getCommandName(), command);
    }

}
