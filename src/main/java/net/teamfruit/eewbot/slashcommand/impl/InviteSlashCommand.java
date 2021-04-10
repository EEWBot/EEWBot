package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import reactor.core.publisher.Mono;

public class InviteSlashCommand implements ISlashCommand {

	@Override
	public ApplicationCommandRequest command() {
		return ApplicationCommandRequest.builder().name("invite").description("招待リンクを入手").build();
	}

	@Override
	public Mono<?> execute(final EEWBot bot, final InteractionCreateEvent event) {
		return event.getInteractionResponse().createFollowupMessage("https://discordapp.com/oauth2/authorize?client_id="+event.getClient().getSelfId().asString()+"&scope=bot");
	}

}
