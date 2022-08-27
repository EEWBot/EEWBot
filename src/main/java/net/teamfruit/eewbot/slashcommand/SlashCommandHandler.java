package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.slashcommand.impl.InviteSlashCommand;

import java.util.HashMap;

public class SlashCommandHandler {

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        InviteSlashCommand invite = new InviteSlashCommand();
        commands.put(invite.getCommandName(), invite);
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
                .flatMap(event -> commands.get(event.getCommandName()).on(bot, event))
                .subscribe();
    }
}
