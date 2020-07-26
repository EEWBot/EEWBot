package net.teamfruit.eewbot.command;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.reaction.ReactionEmoji;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public abstract class ReactionCommand implements ICommand {

	public static final ReactionEmoji EMOJI_Y = ReactionEmoji.unicode("🇾");
	public static final ReactionEmoji EMOJI_N = ReactionEmoji.unicode("🇳");

	private Snowflake messageId;
	private Snowflake commandSenderId;

	public Snowflake getMessageId() {
		return this.messageId;
	}

	public Snowflake getAuthor() {
		return this.commandSenderId;
	}

	protected Message setBotMessage(final Message msg) {
		this.messageId = msg.getId();
		return msg;
	}

	protected Message setAuthor(final Message msg) {
		this.commandSenderId = msg.getAuthor()
				.map(User::getId)
				.orElse(null);
		return msg;
	}

	abstract public Mono<Boolean> onReaction(EEWBot bot, ReactionAddEvent reaction, String lang);
}
