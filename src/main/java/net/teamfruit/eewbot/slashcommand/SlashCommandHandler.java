package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.slashcommand.impl.InviteSlashCommand;
import net.teamfruit.eewbot.slashcommand.impl.SetlSlashCommand;
import reactor.core.publisher.Mono;

import java.util.HashMap;

public class SlashCommandHandler {

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        InviteSlashCommand invite = new InviteSlashCommand();
        commands.put(invite.getCommandName(), invite);

        SetlSlashCommand set = new SetlSlashCommand();
        commands.put(set.getCommandName(), set);
    }

    public SlashCommandHandler(EEWBot bot) {
        if (bot.getConfig().isDebug()) {
            long guildId = 564550533973540885L;
            commands.values().forEach(command -> bot.getClient().getRestClient().getApplicationService().createGuildApplicationCommand(bot.getApplicationId(), guildId, command.buildCommand()).subscribe());
        } else {
            // Global
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
