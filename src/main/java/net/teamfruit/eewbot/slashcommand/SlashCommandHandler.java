package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.rest.service.ApplicationService;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.slashcommand.impl.InviteSlashCommand;
import net.teamfruit.eewbot.slashcommand.impl.SetupSlashCommand;
import reactor.core.publisher.Mono;

import java.util.HashMap;

public class SlashCommandHandler {

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        InviteSlashCommand invite = new InviteSlashCommand();
        commands.put(invite.getCommandName(), invite);

        SetupSlashCommand setup = new SetupSlashCommand();
        commands.put(setup.getCommandName(), setup);
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
                .flatMap(event -> commands.get(event.getCommandName()).on(bot, event)
                        .onErrorResume(err -> event.createFollowup("エラーが発生しました！").then()))
                .subscribe();

        bot.getClient().on(SelectMenuInteractionEvent.class)
                .flatMap(event -> Mono.justOrEmpty(commands.values().stream()
                                .filter(ISelectMenuSlashCommand.class::isInstance)
                                .map(ISelectMenuSlashCommand.class::cast)
                                .filter(command -> command.getCustomIds().contains(event.getCustomId()))
                                .findAny())
                        .flatMap(command -> command.onSelect(bot, event)
                                .onErrorResume(err -> event.createFollowup("エラーが発生しました！").then())))
                .subscribe();
    }
}
