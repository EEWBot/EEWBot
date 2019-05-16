package net.teamfruit.eewbot.command.impl;

import java.awt.Color;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.ReactionCommand;
import net.teamfruit.eewbot.registry.Channel;
import reactor.core.publisher.Mono;

public class RegisterCommand extends ReactionCommand {

	private boolean setup;
	private int setupProgress = -1;

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event) {
		setAuthor(event.getMessage());
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.justOrEmpty(!bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createEmbed(embed -> embed.setTitle("チャンネル登録")
								.setColor(new Color(255, 64, 64))
								.setDescription("このチャンネルはすでに登録されています。"))
								.map(m -> false)))
				.flatMap(channel -> Mono.fromCallable(() -> {
					bot.getChannels().put(channel.getId().asLong(), new Channel());
					bot.getChannelRegistry().save();
					return channel;
				}))
				.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("チャンネル登録")
						.setColor(new Color(7506394))
						.setDescription("チャンネルを登録しました。\nセットアップウィザードを開始しますか(Y/N)？\nキャンセルすると初期設定が適用されます。")))
				.map(this::setBotMessage)
				.flatMap(msg -> msg.addReaction(EMOJI_Y)
						.then(msg.addReaction(EMOJI_N)))
				.then();
	}

	@Override
	public Mono<Boolean> onReaction(final EEWBot bot, final ReactionAddEvent reaction) {
		if (!(reaction.getEmoji().equals(EMOJI_Y)||reaction.getEmoji().equals(EMOJI_N)))
			return Mono.just(false);

		if (!this.setup&&reaction.getEmoji().equals(EMOJI_N))
			return reaction.getChannel()
					.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("チャンネル登録")
							.setColor(new Color(7506394))
							.addField("初期設定を適用しました", bot.getChannels().get(channel.getId().asLong()).toString(), false)))
					.map(m -> true);

		if (!this.setup&&reaction.getEmoji().equals(EMOJI_Y))
			this.setup = true;

		this.setupProgress++;

		final Channel channel = bot.getChannels().get(reaction.getChannelId().asLong());
		final boolean isY = reaction.getEmoji().equals(EMOJI_Y);

		switch (this.setupProgress) {
			case 0:
				return createSetupMessage(reaction, "緊急地震速報 (警報)", "最大震度5弱以上が予想される緊急地震速報を通知しますか(Y/N)？");
			case 1:
				channel.eewAlert = isY;
				return createSetupMessage(reaction, "緊急地震速報 (予報)", "M3.5以上または最大震度3以上が予想される緊急地震速報を通知しますか(Y/N)？");
			case 2:
				channel.eewPrediction = isY;
				return createSetupMessage(reaction, "緊急地震速報 間引きモード", "緊急地震速報更新時に前報と内容が変わらない場合、それを通知しないようにしますか(Y/N)？");
			case 3:
				channel.eewDecimation = isY;
				return createSetupMessage(reaction, "強震モニタ", "緊急地震速報の第一報と最終報時に強震モニタの画像を通知しますか（Y/N)？");
			case 4:
				channel.monitor = isY;
				return createSetupMessage(reaction, "地震情報", "地震情報（震源・震度に関する情報）を通知しますか(Y/N)？");
			case 5:
				channel.quakeInfo = isY;
				return createSetupMessage(reaction, "詳細地震情報", "地震情報（各地の震度に関する情報）を通知しますか(Y/N)？");
			case 6:
				channel.quakeInfoDetail = isY;
				return reaction.getChannel()
						.flatMap(c -> c.createEmbed(embed -> embed.setTitle("チャンネル登録")
								.setColor(new Color(7506394))
								.addField("設定が完了しました", bot.getChannels().get(c.getId().asLong()).toString(), false)))
						.flatMap(c -> Mono.fromCallable(() -> {
							bot.getChannelRegistry().save();
							return channel;
						}))
						.map(m -> true);
			default:
				return Mono.just(true);
		}
	}

	private Mono<Boolean> createSetupMessage(final ReactionAddEvent event, final String name, final String desc) {
		return event.getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("チャンネル登録")
						.setColor(new Color(7506394))
						.addField(name, desc, false)))
				.map(this::setBotMessage)
				.flatMap(msg -> msg.addReaction(EMOJI_Y)
						.then(msg.addReaction(EMOJI_N)))
				.map(m -> false);
	}

}
