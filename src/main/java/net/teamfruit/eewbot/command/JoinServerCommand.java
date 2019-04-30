package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.util.Snowflake;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public class JoinServerCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String[] args) {
		return event.getMessage().getChannel()
				.flatMap(channel -> channel.createMessage(event.getClient().getSelfId()
						.map(Snowflake::asString)
						.map(id -> "https://discordapp.com/oauth2/authorize?client_id="+id+"&scope=bot&permissions=523344")
						.orElse("Id取得に失敗しました")))
				.then();
	}

}
