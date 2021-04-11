package net.teamfruit.eewbot.slashcommand;

import java.util.HashMap;
import java.util.Map;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.rest.RestClient;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.slashcommand.impl.AddSlashCommand;
import net.teamfruit.eewbot.slashcommand.impl.InviteSlashCommand;
import net.teamfruit.eewbot.slashcommand.impl.RemoveSlashCommand;

public class SlashCommandHandler {

	private Map<String, ISlashCommand> commands = new HashMap<>();

	public SlashCommandHandler(final EEWBot bot) {
		final GatewayDiscordClient client = bot.getClient();
		final RestClient restClient = client.getRestClient();

		final long applicationId = restClient.getApplicationId().block();

		// (アップデート等により)消去したコマンドをDiscordから消去
		restClient.getApplicationService().getGlobalApplicationCommands(applicationId)
				.filter(data -> !this.commands.containsKey(data.name()))
				.flatMap(data -> restClient.getApplicationService().deleteGlobalApplicationCommand(applicationId, Long.parseLong(data.id())))
				.subscribe();

		this.commands.put("invite", new InviteSlashCommand());
		this.commands.put("add", new AddSlashCommand());
		this.commands.put("remove", new RemoveSlashCommand());

		this.commands.forEach((k, v) -> restClient.getApplicationService().createGlobalApplicationCommand(applicationId, v.command()).subscribe());

		bot.getClient().on(InteractionCreateEvent.class)
				.filter(event -> this.commands.containsKey(event.getCommandName()))
				.flatMap(event -> event.acknowledge()
						.then(this.commands.get(event.getCommandName()).execute(bot, event))
						.doOnError(t -> {
							Log.logger.error("Error in slashcommands", t);
							event.getInteractionResponse().createFollowupMessage("Error").subscribe();
						}))
				.subscribe();

	}
}
