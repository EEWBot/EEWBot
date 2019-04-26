package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.ReactionAddEvent;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public interface IReactionCommand extends ICommand {

	Mono<Void> onReaction(EEWBot bot, ReactionAddEvent reaction);
}
