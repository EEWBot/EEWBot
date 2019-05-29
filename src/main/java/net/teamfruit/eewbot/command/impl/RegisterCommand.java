package net.teamfruit.eewbot.command.impl;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.command.ReactionCommand;
import net.teamfruit.eewbot.registry.Channel;
import reactor.core.publisher.Mono;

public class RegisterCommand extends ReactionCommand {

	private boolean setup;
	private int setupProgress = -1;

	@Override
	public Mono<Void> execute(final EEWBot bot, final MessageCreateEvent event, final String lang) {
		setAuthor(event.getMessage());
		return event.getMessage().getChannel()
				.filterWhen(channel -> Mono.just(!bot.getChannels().containsKey(channel.getId().asLong()))
						.filter(b -> b)
						.switchIfEmpty(channel.createEmbed(embed -> CommandUtils.createErrorEmbed(embed, lang)
								.setTitle("eewbot.cmd.register.title")
								.setDescription("eewbot.cmd.err.channelalreadyregistered.desc"))
								.map(m -> false)))
				.flatMap(channel -> Mono.fromCallable(() -> {
					bot.getChannels().put(channel.getId().asLong(), new Channel());
					bot.getChannelRegistry().save();
					return channel;
				}))
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed, lang)
						.setTitle("eewbot.cmd.register.title")
						.setDescription("eewbot.cmd.register.desc")))
				.map(this::setBotMessage)
				.flatMap(msg -> msg.addReaction(EMOJI_Y)
						.then(msg.addReaction(EMOJI_N)))
				.then();
	}

	@Override
	public Mono<Boolean> onReaction(final EEWBot bot, final ReactionAddEvent reaction, final String lang) {
		if (!(reaction.getEmoji().equals(EMOJI_Y)||reaction.getEmoji().equals(EMOJI_N)))
			return Mono.just(false);

		if (!this.setup&&reaction.getEmoji().equals(EMOJI_N))
			return reaction.getChannel()
					.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed, lang)
							.setTitle("eewbot.cmd.register.title")
							.addField("eewbot.cmd.register.field.initial.name", bot.getChannels().get(channel.getId().asLong()).toString(), false)))
					.map(m -> true);

		if (!this.setup&&reaction.getEmoji().equals(EMOJI_Y))
			this.setup = true;

		this.setupProgress++;

		final Channel channel = bot.getChannels().get(reaction.getChannelId().asLong());
		final boolean isY = reaction.getEmoji().equals(EMOJI_Y);

		switch (this.setupProgress) {
			case 0:
				return createSetupMessage(reaction, lang, "eewbot.cmd.register.field.eewalert.name", "eewbot.cmd.register.field.eewalert.value");
			case 1:
				channel.eewAlert = isY;
				return createSetupMessage(reaction, lang, "eewbot.cmd.register.field.eewprediction.name", "eewbot.cmd.register.field.eewprediction.value");
			case 2:
				channel.eewPrediction = isY;
				return createSetupMessage(reaction, lang, "eewbot.cmd.register.field.eewdecimation.name", "eewbot.cmd.register.field.eewdecimation.value");
			case 3:
				channel.eewDecimation = isY;
				return createSetupMessage(reaction, lang, "eewbot.cmd.register.field.monitor.name", "eewbot.cmd.register.field.monitor.value");
			case 4:
				channel.monitor = isY;
				return createSetupMessage(reaction, lang, "eewbot.cmd.register.field.quakeinfo.name", "eewbot.cmd.register.field.quakeinfo.value");
			case 5:
				channel.quakeInfo = isY;
				return createSetupMessage(reaction, lang, "eewbot.cmd.register.field.quakeinfodetail.name", "eewbot.cmd.register.field.quakeinfodetail.value");
			case 6:
				channel.quakeInfoDetail = isY;
				return reaction.getChannel()
						.flatMap(c -> c.createEmbed(embed -> CommandUtils.createEmbed(embed, lang)
								.setTitle("eewbot.cmd.register.title")
								.addField("eewbot.cmd.register.field.done.name", bot.getChannels().get(c.getId().asLong()).toString(), false)))
						.flatMap(c -> Mono.fromCallable(() -> {
							bot.getChannelRegistry().save();
							return channel;
						}))
						.map(m -> true);
			default:
				return Mono.error(new IllegalStateException("不正なセットアップ進行状況です"));
		}
	}

	private Mono<Boolean> createSetupMessage(final ReactionAddEvent event, final String lang, final String name, final String value) {
		return event.getChannel()
				.flatMap(channel -> channel.createEmbed(embed -> CommandUtils.createEmbed(embed, lang)
						.setTitle("eewbot.cmd.register.title")
						.addField(name, value, false)))
				.map(this::setBotMessage)
				.flatMap(msg -> msg.addReaction(EMOJI_Y)
						.then(msg.addReaction(EMOJI_N)))
				.map(m -> false);
	}

}
