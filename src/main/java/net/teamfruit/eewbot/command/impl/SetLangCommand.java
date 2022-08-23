package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ReactionCommand;
import net.teamfruit.eewbot.i18n.I18n;
import reactor.core.publisher.Mono;

public class SetLangCommand extends ReactionCommand {

	private String lang;
	private Stage stage = Stage.SERVER;

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		setAuthor(event.getMessage());
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.justOrEmpty(event.getMessage().getContent().split(" "))
						.filterWhen(array -> Mono.just(array.length>=3)
								.filter(b -> b)
								.switchIfEmpty(channel.createMessage(embed -> CommandUtils.createErrorEmbed(lang)
										.title("eewbot.cmd.setlang.title")
										.description("eewbot.cmd.err.arg.desc"))
										.map(m -> false)))
						.filterWhen(array -> Mono.just(I18n.INSTANCE.getLanguages().containsKey(array[2]))
								.filter(b -> b)
								.switchIfEmpty(channel.createMessage(embed -> CommandUtils.createErrorEmbed(lang)
										.title("eewbot.cmd.setlang.title")
										.description("eewbot.cmd.err.fieldnotexits"))
										.map(m -> false)))
						.filter(array -> bot.getChannels().containsKey(channel.getId().asLong()))
						.flatMap(array -> Mono.fromCallable(() -> {
							this.lang = array[2];
							bot.getChannels().get(channel.getId().asLong()).lang = array[2];
							bot.getChannelRegistry().save();
							return true;
						})))
				.flatMap(channel -> channel.createMessage(embed -> CommandUtils.createEmbed(lang)
						.title("eewbot.cmd.setlang.title")
						.description("eewbot.cmd.setlang.desc.server")))
				.map(this::setBotMessage)
				.flatMap(msg -> msg.addReaction(EMOJI_Y)
						.then(msg.addReaction(EMOJI_N)))
				.then();
	}

	@Override
	public Mono<Boolean> onReaction(final EEWBot bot, final ReactionAddEvent reaction, final String lang) {
		if (!(reaction.getEmoji().equals(EMOJI_Y)||reaction.getEmoji().equals(EMOJI_N)))
			return Mono.just(false);

		if (this.stage==Stage.SERVER) {
			this.stage = Stage.ALL_CHNNEL;

			return reaction.getChannel()
					.flatMap(c -> c.createMessage(embed -> CommandUtils.createEmbed(lang)
							.title("eewbot.cmd.setlang.title")
							.description("eewbot.cmd.setlang.desc.allchannel")))
					.map(this::setBotMessage)
					.flatMap(msg -> msg.addReaction(EMOJI_Y)
							.then(msg.addReaction(EMOJI_N)))
					.filter(m -> reaction.getEmoji().equals(EMOJI_Y)&&reaction.getGuildId().isPresent())
					.flatMap(m -> Mono.fromCallable(() -> {
						bot.getGuilds().get(reaction.getGuildId().get().asLong()).setLang(this.lang);
						bot.getGuildsRegistry().save();
						return false;
					}));
		} else if (this.stage==Stage.ALL_CHNNEL) {
			this.stage = reaction.getEmoji().equals(EMOJI_N)&&bot.getChannels().containsKey(reaction.getChannelId().asLong()) ? Stage.CHANNEL : null;

			return reaction.getChannel()
					.flatMap(c -> c.createMessage(embed -> CommandUtils.createEmbed(lang)
							.title("eewbot.cmd.setlang.title")
							.description(this.stage==Stage.CHANNEL ? "eewbot.cmd.setlang.desc.channel" : "eewbot.cmd.setlang.desc.done")))
					.map(this::setBotMessage)
					.flatMap(msg -> Mono.just(msg)
							.filter(m -> this.stage==Stage.CHANNEL)
							.flatMap(m -> msg.addReaction(EMOJI_Y)
									.then(msg.addReaction(EMOJI_N))))
					.map(m -> this.stage!=Stage.CHANNEL)
					.filter(b -> reaction.getEmoji().equals(EMOJI_Y))
					.flatMap(v -> reaction.getGuild()
							.flatMap(guild -> guild.getChannels()
									.filter(channel -> bot.getChannels().containsKey(channel.getId().asLong()))
									.map(channel -> {
										bot.getChannels().get(channel.getId().asLong()).lang = this.lang;
										return channel;
									})
									.next()))
					.flatMap(m -> Mono.fromCallable(() -> {
						bot.getChannelRegistry().save();
						return this.stage!=Stage.CHANNEL;
					}));
		} else if (this.stage==Stage.CHANNEL)
			return reaction.getChannel()
					.flatMap(c -> c.createMessage(embed -> CommandUtils.createEmbed(lang)
							.title("eewbot.cmd.setlang.title")
							.description("eewbot.cmd.setlang.desc.done")))
					.flatMap(m -> Mono.fromCallable(() -> {
						if (reaction.getEmoji().equals(EMOJI_Y)) {
							bot.getChannels().get(reaction.getChannelId().asLong()).lang = this.lang;
							bot.getChannelRegistry().save();
						}
						return true;
					}))
					.map(m -> true);
		return null;
	}

	public enum Stage {
		SERVER,
		CHANNEL,
		ALL_CHNNEL
	}
}
