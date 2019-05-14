package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.util.Snowflake;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public abstract class ReactionCommand implements ICommand {

	private Snowflake messageId;

	public Snowflake getId() {
		return this.messageId;
	}

	public Message set(final Message msg) {
		this.messageId = msg.getId();
		return msg;
	}

	abstract Mono<Void> onReaction(EEWBot bot, ReactionAddEvent reaction);
}
