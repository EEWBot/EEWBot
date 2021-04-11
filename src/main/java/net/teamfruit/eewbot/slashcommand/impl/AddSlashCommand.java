package net.teamfruit.eewbot.slashcommand.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData.Builder;
import discord4j.rest.util.ApplicationCommandOptionType;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.ISlashCommand;
import reactor.core.publisher.Mono;

public class AddSlashCommand implements ISlashCommand {

	final Map<String, String> choices = new LinkedHashMap<>();

	@Override
	public ApplicationCommandRequest command() {
		this.choices.put("all", "全て");
		this.choices.put("eewAlert", "緊急地震速報(警報)");
		this.choices.put("eewPrediction", "緊急地震速報(予報)");
		this.choices.put("quakeInfo", "地震情報");
		this.choices.put("monitor", "強震モニタ");

		final Builder builder = ApplicationCommandOptionData.builder()
				.name("情報の種類")
				.description("通知する情報の種類")
				.type(ApplicationCommandOptionType.STRING.getValue())
				.required(true);
		this.choices.forEach((value, name) -> builder.addChoice(ApplicationCommandOptionChoiceData.builder()
				.name(name)
				.value(value)
				.build()));

		return ApplicationCommandRequest.builder()
				.name("add")
				.description("Botが現在のチャンネルに投稿する情報を設定")
				.addOption(builder.build())
				.build();
	}

	@Override
	public Mono<?> execute(final EEWBot bot, final InteractionCreateEvent event) throws Exception {
		if (!event.getInteraction().getGuildId().isPresent())
			return event.getInteractionResponse().createFollowupMessage("DMチャンネルは利用できません (開発中)");

		final PermissionSet permission = event.getInteraction().getChannel().flatMap(c -> c.getEffectivePermissions(bot.getClient().getSelfId())).block();
		if (!permission.contains(Permission.VIEW_CHANNEL))
			return event.getInteractionResponse().createFollowupMessage("Botはこのチャンネルを閲覧出来ません");
		if (!permission.contains(Permission.SEND_MESSAGES))
			return event.getInteractionResponse().createFollowupMessage("Botはチャンネルにメッセージを投稿する権限がありません");

		final long channelID = event.getInteraction().getChannelId().asLong();
		Channel channel = bot.getChannels().get(channelID);
		if (channel==null) {
			channel = new Channel(false, false, true, false, false, SeismicIntensity.ONE);
			bot.getChannelsLock().writeLock().lock();
			bot.getChannels().put(channelID, channel);
			bot.getChannelsLock().writeLock().unlock();
		}

		final ApplicationCommandInteractionOption option = event.getInteraction().getCommandInteraction().getOptions().get(0);
		if (option.getValue().get().asString().equals("all")) {
			channel.eewAlert = true;
			channel.eewPrediction = true;
			channel.quakeInfo = true;
			channel.monitor = true;

			bot.getChannelRegistry().save();
			return event.getInteractionResponse().createFollowupMessage("全ての種類の情報をこのチャンネルに投稿します！");

		} else {
			if (channel.value(option.getValue().get().asString()))
				return event.getInteractionResponse().createFollowupMessage(this.choices.get(option.getValue().get().asString())+" は既に登録されています");

			channel.set(option.getValue().get().asString(), true);

			bot.getChannelRegistry().save();
			return event.getInteractionResponse().createFollowupMessage(this.choices.get(option.getValue().get().asString())+" をこのチャンネルに投稿します！");
		}
	}

}
