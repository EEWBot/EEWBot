package net.teamfruit.eewbot.command;

import static net.teamfruit.eewbot.command.CommandUtils.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.impl.AddCommand;
import net.teamfruit.eewbot.command.impl.DetailsCommand;
import net.teamfruit.eewbot.command.impl.HelpCommand;
import net.teamfruit.eewbot.command.impl.JoinServerCommand;
import net.teamfruit.eewbot.command.impl.MonitorCommand;
import net.teamfruit.eewbot.command.impl.RegisterCommand;
import net.teamfruit.eewbot.command.impl.ReloadCommand;
import net.teamfruit.eewbot.command.impl.RemoveCommand;
import net.teamfruit.eewbot.command.impl.TimeCommand;
import net.teamfruit.eewbot.command.impl.TimeFixCommand;
import net.teamfruit.eewbot.command.impl.UnRegisterCommand;
import reactor.core.publisher.Mono;

public class CommandHandler {

	public static Map<String, Supplier<ICommand>> commands = new HashMap<>();

	static {
		commands.put("unregister", wrap(new UnRegisterCommand()));
		commands.put("details", wrap(new DetailsCommand()));
		commands.put("add", wrap(new AddCommand()));
		commands.put("remove", wrap(new RemoveCommand()));
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
										.flatMap(channel -> channel.createEmbed(embed -> createErrorEmbed(embed, getLangage(bot, event))
												.setTitle("コマンドが見つかりません")
												.setDescription("`!eew help` でコマンド一覧を確認出来ます")))
										.map(m -> false)))
						.filterWhen(array -> Mono.just(!this.bot.getConfig().isEnablePermission())
								.flatMap(b -> Mono.justOrEmpty(event.getMember())
										.map(member -> b||userHasPermission(bot, member.getId().asLong(), array[1])))
								.filter(b -> b)
								.switchIfEmpty(event.getMessage().getChannel()
										.flatMap(channel -> channel.createEmbed(embed -> createErrorEmbed(embed, getLangage(bot, event))
												.setTitle("権限がありません")
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
						.flatMap(cmd -> cmd.execute(bot, event, getLangage(bot, event)))
						.doOnError(err -> event.getMessage().getChannel()
								.flatMap(channel -> channel.createEmbed(embed -> createErrorEmbed(embed, getLangage(bot, event))
										.setTitle("エラーが発生しました")
										.setDescription(ExceptionUtils.getMessage(err))))
								.subscribe())
						.onErrorResume(e -> Mono.empty()))
				.subscribe();

		this.bot.getClient().getEventDispatcher().on(ReactionAddEvent.class)
				.filter(event -> !event.getUserId().equals(bot.getClient().getSelfId().orElse(null)))
				.flatMap(event -> Mono.justOrEmpty(this.reactionWaitingList.get(event.getMessageId()))
						.filter(cmd -> event.getUserId().equals(cmd.getAuthor()))
						.filterWhen(cmd -> cmd.onReaction(bot, event, getLangage(bot, event)))
						.map(b -> {
							this.reactionWaitingList.remove(event.getMessageId());
							return true;
						})
						.doOnError(err -> event.getChannel()
								.flatMap(channel -> channel.createEmbed(embed -> createErrorEmbed(embed, getLangage(bot, event))
										.setTitle("エラーが発生しました")
										.setDescription(ExceptionUtils.getMessage(err))))
								.subscribe())
						.onErrorResume(e -> Mono.empty()))
				.subscribe();
	}

	public static Supplier<ICommand> wrap(final ICommand command) {
		return () -> command;
	}

}
