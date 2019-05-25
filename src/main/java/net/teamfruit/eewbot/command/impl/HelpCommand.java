package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ICommand;
import reactor.core.publisher.Mono;

public class HelpCommand implements ICommand {

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, String lang) {
		return event.getMessage().getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed)
						.setTitle("Help")
						.addField("register", "通知するチャンネルを登録し、セットアップします", true)
						.addField("unregister", "チャンネルの登録を解除します", true)
						.addField("details", "登録されたチャンネルの設定を表示します", true)
						.addField("add", "チャンネルに通知される情報を追加します", true)
						.addField("remove", "チャンネルに通知される情報を消去します", true)
						.addField("monitor", "現在の強震モニタの画像を取得します", true)
						.addField("time", "Botの時刻同期情報を表示します", true)
						.addField("timefix", "Botの時刻を強制的に修正します", true)
						.addField("joinserver", "Botの招待リンクを表示します", true)
						.addField("reload", "ConfigとPermissionsをリロードします", true)
						.addField("help", "ヘルプを表示します", true)))
				.then();
	}

}
