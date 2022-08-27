package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.slashcommand.impl.InviteCommand;

import java.util.HashMap;

public class SlashCommandHandler {

    public static HashMap<String, ISlashCommand> commands = new HashMap<>();

    static {
        InviteCommand invite = new InviteCommand();
        commands.put(invite.getCommandName(), invite);
    }

    public SlashCommandHandler(EEWBot bot) {
        commands.entrySet().forEach(command -> bot.getClient().getRestClient().getApplicationService().createGuildApplicationCommand(bot.getApplicationId(), 564550533973540885L, command.getValue().buildCommand()));

        bot.getClient().on(ApplicationCommandInteractionEvent.class)
                .filter(event -> commands.containsKey(event.getCommandName()))
                .flatMap(event -> commands.get(event.getCommandName()).on(event))
                .subscribe();
    }
}
