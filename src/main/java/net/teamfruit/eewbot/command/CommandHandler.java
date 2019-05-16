package net.teamfruit.eewbot.command;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.Permission;
import reactor.core.publisher.Mono;

public class CommandHandler {

	public static Map<String, Supplier<ICommand>> commands = new HashMap<>();

	static {
		commands.put("unregister", wrap(new UnRegisterCommand()));
		commands.put("details", wrap(new DetailsCommand()));
		commands.put("monitor", wrap(new MonitorCommand()));
		commands.put("time", wrap(new TimeCommand()));
		commands.put("timefix", wrap(new TimeFixCommand()));
		commands.put("joinserver", wrap(new JoinServerCommand()));
		commands.put("reload", wrap(new ReloadCommand()));
		commands.put("help", wrap(new HelpCommand()));

		commands.put("register", () -> new RegisterCommand());
	}

	private final EEWBot bot;
	private final ReactionWaitingList reactionWaitingList;

	public CommandHandler(final EEWBot bot) {
		this.bot = bot;
		this.reactionWaitingList = new ReactionWaitingList(bot.getExecutor().getExecutor());

		this.bot.getClient().getEventDispatcher().on(MessageCreateEvent.class)
				.flatMap(event -> Mono.justOrEmpty(event.getMessage().getContent())
						.filter(str -> str.startsWith("!eew"))
						.map(str -> str.split(" "))
						.filterWhen(array -> Mono.just(array.length>=2&&commands.containsKey(array[1]))
								.filter(b -> b)
								.switchIfEmpty(event.getMessage().getChannel()
										.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("コマンドが見つかりません")
												.setColor(new Color(255, 64, 64))
												.setDescription("`!eew help` でコマンド一覧を確認出来ます")))
										.map(m -> false)))
						.filterWhen(array -> Mono.just(!this.bot.getConfig().isEnablePermission())
								.flatMap(b -> Mono.justOrEmpty(event.getMember())
										.map(member -> b||userHasPermission(member.getId().asLong(), array[1])))
								.filter(b -> b)
								.switchIfEmpty(event.getMessage().getChannel()
										.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("権限がありません")
												.setColor(new Color(255, 64, 64))
												.setDescription("管理者にお問い合わせ下さい")))
										.map(m -> false)))
						.map(array -> commands.get(array[1]).get())
						.filterWhen(cmd -> Mono.just(!(cmd instanceof ReactionCommand))
								.filter(b -> b)
								.switchIfEmpty(Mono.just(cmd)
										.cast(ReactionCommand.class)
										.map(c -> {
											this.reactionWaitingList.add(c);
											return true;
										})))
						.flatMap(cmd -> cmd.execute(bot, event))
						.doOnError(err -> event.getMessage().getChannel()
								.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("エラーが発生しました")
										.setColor(new Color(255, 64, 64))
										.setFooter("ご迷惑をおかけし申し訳ありません。", null)
										.setDescription(ExceptionUtils.getMessage(err))))
								.subscribe())
						.onErrorResume(e -> Mono.empty()))
				.subscribe();

		this.bot.getClient().getEventDispatcher().on(ReactionAddEvent.class)
				.filter(event -> !event.getUserId().equals(bot.getClient().getSelfId().orElse(null)))
				.flatMap(event -> Mono.justOrEmpty(this.reactionWaitingList.get(event.getMessageId()))
						.filter(cmd -> event.getUserId().equals(cmd.getAuthor()))
						.filterWhen(cmd -> cmd.onReaction(bot, event))
						.map(b -> {
							this.reactionWaitingList.remove(event.getMessageId());
							return true;
						})
						.doOnError(err -> event.getChannel()
								.flatMap(channel -> channel.createEmbed(embed -> embed.setTitle("エラーが発生しました")
										.setColor(new Color(255, 64, 64))
										.setFooter("ご迷惑をおかけし申し訳ありません。", null)
										.setDescription(ExceptionUtils.getMessage(err))))
								.subscribe())
						.onErrorResume(e -> Mono.empty()))
				.subscribe();
	}

	public static Supplier<ICommand> wrap(final ICommand command) {
		return () -> command;
	}

	public static boolean userHasPermission(final long userid, final String command) {
		return EEWBot.instance.getPermissions().values().stream()
				.filter(permission -> permission.getUserid().contains(userid))
				.findAny().orElse(EEWBot.instance.getPermissions().getOrDefault("everyone", Permission.DEFAULT_EVERYONE))
				.getCommand().contains(command);
	}
}
