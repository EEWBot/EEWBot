package net.teamfruit.eewbot.slashcommand.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData.Builder;
import discord4j.rest.util.ApplicationCommandOptionType;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import reactor.core.publisher.Mono;

public class SensSlashCommand implements ISlashCommand {

	final Map<String, String> choices = new LinkedHashMap<>();

	@Override
	public ApplicationCommandRequest command() {
		this.choices.put("1", "震度1以上");
		this.choices.put("2", "震度2以上");
		this.choices.put("3", "震度3以上");
		this.choices.put("4", "震度4以上");
		this.choices.put("5弱", "震度5弱以上");
		this.choices.put("5強", "震度5強以上");
		this.choices.put("6弱", "震度6弱以上");
		this.choices.put("6強", "震度6強以上");
		this.choices.put("7", "震度7");

		final Builder builder = ApplicationCommandOptionData.builder()
				.name("震度")
				.description("震度制限")
				.type(ApplicationCommandOptionType.STRING.getValue())
				.required(true);
		this.choices.forEach((value, name) -> builder.addChoice(ApplicationCommandOptionChoiceData.builder()
				.name(name)
				.value(value)
				.build()));

		return ApplicationCommandRequest.builder()
				.name("sens")
				.description("Botが投稿する情報を震度で制限")
				.addOption(builder.build())
				.build();

	}

	@Override
	public Mono<?> execute(final EEWBot bot, final InteractionCreateEvent event) throws Exception {
		if (!event.getInteraction().getGuildId().isPresent())
			return event.getInteractionResponse().createFollowupMessage("DMチャンネルは利用できません (開発中)");

		final long channelID = event.getInteraction().getChannelId().asLong();
		final Channel channel = bot.getChannels().get(channelID);
		if (channel==null)
			return event.getInteractionResponse().createFollowupMessage("このチャンネルはなにも設定されていません");

		final ApplicationCommandInteractionOption option = event.getInteraction().getCommandInteraction().getOptions().get(0);
		final Optional<SeismicIntensity> intensity = SeismicIntensity.get(option.getValue().get().asString());
		channel.minIntensity = intensity.get();
		bot.getChannelRegistry().save();

		return event.getInteractionResponse().createFollowupMessage("Botが情報を投稿する震度を "+this.choices.get(option.getValue().get().asString())+" に設定しました！");
	}

}
