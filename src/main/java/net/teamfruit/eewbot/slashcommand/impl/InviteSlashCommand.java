package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.channel.Channel;
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
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        return event.reply("https://discord.com/api/oauth2/authorize?client_id=" + event.getClient().getSelfId().asString() + "&permissions=275414829120&scope=bot%20applications.commands")
                .withEphemeral(true);
    }
}
