package net.teamfruit.eewbot;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public class EEWService {

	private final EEWBot bot;

	public EEWService(final EEWBot bot) {
		this.bot = bot;
	}

	public Mono<Void> sendMessage(final String key, final Consumer<? super MessageCreateSpec> spec) {
		return sendMessage(channel -> channel.getElement(key).get(), spec);
	}

	public Mono<Void> sendMessage(final Predicate<net.teamfruit.eewbot.registry.Channel> filter, final Consumer<? super MessageCreateSpec> spec) {
		return Mono.whenDelayError(this.bot.getChannels().values().stream().flatMap(List::stream)
				.filter(filter::test)
				.map(channel -> this.bot.getClient().getChannelById(Snowflake.of(channel.id))
						.filter(c -> c.getType()==Channel.Type.GUILD_TEXT)
						.cast(TextChannel.class)
						.flatMap(tc -> tc.createMessage(spec)))
				.collect(Collectors.toList()));
	}

}
