package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class JoinServerCommand implements ICommand {

    @Override
    public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
        return event.getMessage().getChannel()
                .flatMap(channel -> channel.createMessage("https://discord.com/api/oauth2/authorize?client_id=" + event.getClient().getSelfId().asString() + "&permissions=275414829120&scope=bot%20applications.commands"))
                //						.orElse("eewbot.cmd.joinserver.failed")))
                .then();
    }

}
