package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.util.Snowflake;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public abstract class ReactionCommand implements ICommand {

	private Snowflake messageId;

	public Snowflake getId() {
		return this.messageId;
	}

	abstract Mono<Void> onReaction(EEWBot bot, ReactionAddEvent reaction);
}
