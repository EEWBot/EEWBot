package net.teamfruit.eewbot.command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.impl.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static net.teamfruit.eewbot.command.CommandUtils.*;

public class CommandHandler {

    public static Map<String, Supplier<ICommand>> commands = new HashMap<>();

    static {
        commands.put("unregister", wrap(new UnRegisterCommand()));
        commands.put("detail", wrap(new DetailCommand()));
        commands.put("add", wrap(new AddCommand()));
        commands.put("remove", wrap(new RemoveCommand()));
//        commands.put("monitor", wrap(new MonitorCommand()));
        commands.put("time", wrap(new TimeCommand()));
        commands.put("timefix", wrap(new TimeFixCommand()));
        commands.put("joinserver", wrap(new JoinServerCommand()));
        commands.put("reload", wrap(new ReloadCommand()));
        commands.put("quakeinfo", wrap(new QuakeInfoCommand()));
        commands.put("sensitivity", wrap(new SensitivityCommand()));
        commands.put("help", wrap(new HelpCommand()));

        commands.put("register", RegisterCommand::new);
//        commands.put("setlang", SetLangCommand::new);
    }

    private final EEWBot bot;
    private final ReactionWaitingList reactionWaitingList;

    public CommandHandler(final EEWBot bot) {
        this.bot = bot;
        this.reactionWaitingList = new ReactionWaitingList(bot.getExecutor().getExecutor());

        this.bot.getClient().on(MessageCreateEvent.class)
                .flatMap(event -> Mono.justOrEmpty(event.getMessage().getContent())
                        .filter(str -> str.startsWith("!eew"))
                        .map(str -> str.split(" "))
                        .filterWhen(array -> Mono.just(array.length >= 2 && commands.containsKey(array[1]))
                                .filter(b -> b)
                                .switchIfEmpty(event.getMessage().getChannel()
                                        .flatMap(channel -> channel.createMessage(createErrorEmbed(getLanguage(bot, event))
                                                .title("eewbot.cmd.err.unknown.title")
                                                .description("eewbot.cmd.err.unknown.desc")
                                                .build()))
                                        .map(m -> false)))
                        .filterWhen(array -> Mono.just(!this.bot.getConfig().isEnablePermission())
                                .flatMap(b -> Mono.justOrEmpty(event.getMember())
                                        .map(member -> b || userHasPermission(bot, member.getId().asLong(), array[1])))
                                .filter(b -> b)
                                .switchIfEmpty(event.getMessage().getChannel()
                                        .flatMap(channel -> channel.createMessage(createErrorEmbed(getLanguage(bot, event))
                                                .title("eewbot.cmd.err.permission.title")
                                                .description("eewbot.cmd.err.permission.desc")
                                                .build()))
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
                        .flatMap(cmd -> cmd.execute(bot, event, getLanguage(bot, event)))
                        .doOnError(err -> event.getMessage().getChannel()
                                .flatMap(channel -> channel.createMessage(createErrorEmbed(getLanguage(bot, event))
                                        .title("eewbot.cmd.err.error.title")
                                        .description(ExceptionUtils.getMessage(err))
                                        .build()))
                                .subscribe())
                        .onErrorResume(e -> Mono.empty()))
                .subscribe();

        this.bot.getClient().on(ReactionAddEvent.class)
                .filter(event -> !event.getUserId().equals(bot.getClient().getSelfId()))
                .flatMap(event -> Mono.justOrEmpty(this.reactionWaitingList.get(event.getMessageId()))
                        .filter(cmd -> event.getUserId().equals(cmd.getAuthor()))
                        .filterWhen(cmd -> cmd.onReaction(bot, event, getLanguage(bot, event)))
                        .map(b -> {
                            this.reactionWaitingList.remove(event.getMessageId());
                            return true;
                        })
                        .doOnError(err -> event.getChannel()
                                .flatMap(channel -> channel.createMessage(createErrorEmbed(getLanguage(bot, event))
                                        .title("eewbot.cmd.err.error.title")
                                        .description(ExceptionUtils.getMessage(err))
                                        .build()))
                                .subscribe())
                        .onErrorResume(e -> Mono.empty()))
                .subscribe();
    }

    public static Supplier<ICommand> wrap(final ICommand command) {
        return () -> command;
    }

}
