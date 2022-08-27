package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import reactor.core.publisher.Mono;

public class InviteSlashCommand implements ISlashCommand {


    @Override
    public String getCommandName() {
        return "invite";
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("招待URLを取得します。")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event) {
        return event.reply("https://discordapp.com/oauth2/authorize?client_id="+event.getClient().getSelfId().asString()+"&scope=bot&permissions=523344")
                .withEphemeral(true);
    }
}
