package net.teamfruit.eewbot.command;

import java.awt.Color;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

public class HelpCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String[] args) {
		return event.getMessage().getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("Help")
						.setColor(new Color(7506394))
						.addField("monitor", "現在の強震モニタの画像を取得します", true)
						.addField("time", "Botの時刻同期情報を表示します", true)
						.addField("timefix", "Botの時刻を強制的に修正します", true)
						.addField("joinserver", "Botの招待リンクを表示します", true)
						.addField("help", "ヘルプを表示します", true)))
				.then();
	}

}
