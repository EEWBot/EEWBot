package net.teamfruit.eewbot.command;

import java.awt.Color;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public class ReloadCommand implements ICommand {

	@Override
	public Mono<Message> execute(final EEWBot bot, final MessageCreateEvent event, final String[] args) {
		return Mono.zip(event.getMessage().getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("設定リロード")
						.setColor(new Color(7506394))
						.setDescription("処理中"))),
				Mono.fromCallable(() -> {
					bot.getConfigRegistry().load();
					bot.getPermissionsRegistry().load();
					return true;
				}))
				.flatMap(tuple -> tuple.getT1().edit(spec -> spec.setEmbed(embed -> embed.setTitle("設定リロード")
						.setColor(new Color(7506394))
						.setDescription("ConfigとPermissionsのリロードが完了しました"))));
	}

}
