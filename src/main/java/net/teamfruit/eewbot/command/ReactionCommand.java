package net.teamfruit.eewbot.command;

import java.util.Optional;

import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public abstract class ReactionCommand implements ICommand {

	private Snowflake messageId;
	private Optional<User> user;

	public Snowflake getMessageId() {
		return this.messageId;
	}

	public Optional<User> getAuthor() {
		return this.user;
	}

	public Message set(final Message msg) {
		this.messageId = msg.getId();
		this.user = msg.getAuthor();
		return msg;
	}

	abstract public Mono<Boolean> onReaction(EEWBot bot, ReactionAddEvent reaction);
}
