package net.teamfruit.eewbot.command;

import java.awt.Color;
import java.time.ZonedDateTime;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import reactor.core.publisher.Mono;

public class TimeFixCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event) {
		return Mono.zip(event.getMessage().getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("時刻同期")
						.setColor(new Color(7506394))
						.setDescription("取得中"))),
				bot.getExecutor().getProvider().fetch())
				.flatMap(tuple -> tuple.getT1().edit(spec -> spec.setEmbed(embed -> embed.setTitle("時刻同期")
						.setColor(new Color(7506394))
						.addField("現在時刻(コンピューター)", ZonedDateTime.now(TimeProvider.ZONE_ID).toString(), false)
						.addField("現在時刻(オフセット)", bot.getExecutor().getProvider().now().toString(), false)
						.addField("オフセット(ミリ秒)", String.valueOf(bot.getExecutor().getProvider().getOffset()), false))))
				.then();
	}

}
