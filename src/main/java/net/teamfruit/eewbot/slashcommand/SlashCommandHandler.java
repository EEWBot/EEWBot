package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.*;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionCallbackSpec;
import discord4j.rest.service.ApplicationService;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.impl.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;

public class SlashCommandHandler {

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        registerCommand(new SetupSlashCommand());
        registerCommand(new QuakeInfoSlashCommand());
        registerCommand(new TimeSlashCommand());
        registerCommand(new InviteSlashCommand());
        registerCommand(new TestMessageSlashCommand());
        registerCommand(new HelpSlashCommand());
    }

    public SlashCommandHandler(EEWBot bot) {
        ApplicationService service = bot.getClient().getRestClient().getApplicationService();
        if (bot.getConfig().isDebug()) {
            long guildId = 564550533973540885L;
            service.getGuildApplicationCommands(bot.getApplicationId(), guildId)
                    .filter(data -> !commands.containsKey(data.name()))
                    .flatMap(data -> service.deleteGuildApplicationCommand(bot.getApplicationId(), guildId, data.id().asLong()))
                    .subscribe();

            commands.values().forEach(command -> service.createGuildApplicationCommand(bot.getApplicationId(), guildId, command.buildCommand()).subscribe());
        } else {
            service.getGlobalApplicationCommands(bot.getApplicationId())
                    .filter(data -> !commands.containsKey(data.name()))
                    .flatMap(data -> service.deleteGlobalApplicationCommand(bot.getApplicationId(), data.id().asLong()))
                    .subscribe();

            commands.values().forEach(command -> service.createGlobalApplicationCommand(bot.getApplicationId(), command.buildCommand()).subscribe());
        }

        bot.getClient().on(ApplicationCommandInteractionEvent.class)
                .flatMap(event -> Mono.just(event.getCommandName())
                        .filter(name -> commands.containsKey(name))
                        .map(commands::get)
                        .flatMap(cmd -> cmd.isDefer() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeral())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> cmd.on(bot, event, getLanguage(bot, event)))
                        .doOnError(err -> Log.logger.error("Error during {} command", event.getCommandName(), err))
                        .onErrorResume(err -> replyOrFollowUp(event, commands.get(event.getCommandName()).isDefer(), SlashCommandUtils.createErrorEmbed(getLanguage(bot, event))
                                .title("eewbot.scmd.error")
                                .description(ExceptionUtils.getMessage(err))
                                .build()
                        )))
                .subscribe(null, err -> Log.logger.error("Unhandled exception during ApplicationCommandInteractionEvent handling", err));

        bot.getClient().on(SelectMenuInteractionEvent.class)
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(ISelectMenuSlashCommand.class::isInstance)
                                .map(ISelectMenuSlashCommand.class::cast)
                                .filter(cmd -> cmd.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(cmd -> cmd.isDeferOnSelect() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralOnSelect())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> cmd.onSelect(bot, event, getLanguage(bot, event)))
                        .doOnError(err -> Log.logger.error("Error during {} action", event.getCustomId(), err))
                        .onErrorResume(err -> Mono.empty()))
                .onErrorResume(e -> Mono.empty())
                .subscribe(null, err -> Log.logger.error("Unhandled exception during SelectMenuInteractionEvent handling", err));

        bot.getClient().on(ButtonInteractionEvent.class)
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(IButtonSlashCommand.class::isInstance)
                                .map(IButtonSlashCommand.class::cast)
                                .filter(cmd -> cmd.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(cmd -> cmd.isDeferOnClick() ? event.deferReply(InteractionCallbackSpec.builder()
                                .ephemeral(cmd.isEphemeralOnClick())
                                .build()).thenReturn(cmd) : Mono.defer(() -> Mono.just(cmd)))
                        .flatMap(cmd -> cmd.onClick(bot, event, getLanguage(bot, event)))
                        .doOnError(err -> Log.logger.error("Error during {} action", event.getCustomId(), err))
                        .onErrorResume(err -> Mono.empty()))
                .onErrorResume(e -> Mono.empty())
                .subscribe(null, err -> Log.logger.error("Unhandled exception during ButtonInteractionEvent handling", err));
    }

    public static void registerCommand(ISlashCommand command) {
        commands.put(command.getCommandName(), command);
    }

    public static String getLanguage(EEWBot bot, InteractionCreateEvent event) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        if (channel == null)
            return bot.getConfig().getDefaultLanuage();
        return channel.getLang();
    }

    public static Mono<Void> replyOrFollowUp(DeferrableInteractionEvent event, boolean defer, EmbedCreateSpec spec) {
        if (defer)
            return event.createFollowup().withEmbeds(spec)
                    .doOnError(err -> Log.logger.error("Error during follow-up message", err))
                    .then();
        return event.reply().withEmbeds(spec)
                .doOnError(err -> Log.logger.error("Error during reply", err));
    }
}
