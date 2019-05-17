package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class ReloadCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event) {
		return Mono.zip(event.getMessage().getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createBaseEmbed(embed)
						.setTitle("設定リロード")
						.setDescription("処理中"))),
				Mono.fromCallable(() -> {
					bot.getConfigRegistry().load();
					bot.getPermissionsRegistry().load();
					return true;
				}))
				.flatMap(tuple -> tuple.getT1().edit(spec -> spec.setEmbed(embed -> CommandUtils.createBaseEmbed(embed)
						.setTitle("設定リロード")
						.setDescription("ConfigとPermissionsのリロードが完了しました"))))
				.then();
	}

}
