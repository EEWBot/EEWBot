package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.rest.service.ApplicationService;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.impl.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;

public class SlashCommandHandler {

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        registerCommand(new SetupSlashCommand());
        registerCommand(new QuakeInfoSlashCommand());
        registerCommand(new MonitorSlashCommand());
        registerCommand(new TimeSlashCommand());
        registerCommand(new InviteSlashCommand());
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
                .filter(event -> commands.containsKey(event.getCommandName()))
                .flatMap(event -> commands.get(event.getCommandName()).on(bot, event, getLanguage(bot, event))
                        .onErrorResume(err -> event.createFollowup(I18n.INSTANCE.get(getLanguage(bot, event), "eewbot.scmd.error")).then()))
                .subscribe();

        bot.getClient().on(SelectMenuInteractionEvent.class)
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(ISelectMenuSlashCommand.class::isInstance)
                                .map(ISelectMenuSlashCommand.class::cast)
                                .filter(command -> command.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(command -> command.onSelect(bot, event, getLanguage(bot, event))
                                .onErrorResume(err -> event.createFollowup(I18n.INSTANCE.get(getLanguage(bot, event), "eewbot.scmd.error")).then())))
                .subscribe();

        bot.getClient().on(ButtonInteractionEvent.class)
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(IButtonSlashCommand.class::isInstance)
                                .map(IButtonSlashCommand.class::cast)
                                .filter(command -> command.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(command -> command.onClick(bot, event, getLanguage(bot, event))
                                .onErrorResume(err -> event.createFollowup(I18n.INSTANCE.get(getLanguage(bot, event), "eewbot.scmd.error")).then())))
                .subscribe();
    }

    public static void registerCommand(ISlashCommand command) {
        commands.put(command.getCommandName(), command);
    }

    public static String getLanguage(EEWBot bot, InteractionCreateEvent event) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        if (channel == null)
            return bot.getConfig().getDefaultLanuage();
        return channel.lang;
    }
}
