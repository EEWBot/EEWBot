package net.teamfruit.eewbot;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import discord4j.core.DiscordClient;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.registry.Channel;
import reactor.core.publisher.Mono;

public class EEWService {

	private final DiscordClient client;
	private final Map<Long, Channel> channels;

	public EEWService(final DiscordClient client, final Map<Long, Channel> map) {
		this.client = client;
		this.channels = map;
	}

	public Mono<Void> sendMessage(final String key, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		return sendMessage(channel -> channel.value(key), spec);
	}

	public Mono<Void> sendMessage(final Predicate<Channel> filter, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		return Mono.whenDelayError(this.channels.entrySet().stream()
				.filter(entry -> filter.test(entry.getValue()))
				.map(entry -> this.client.getChannelById(Snowflake.of(entry.getKey()))
						.filter(c -> c.getType()==discord4j.core.object.entity.Channel.Type.GUILD_TEXT)
						.cast(TextChannel.class)
						.flatMap(tc -> tc.createMessage(spec.apply(entry.getValue().lang))))
				.collect(Collectors.toList()));
	}

}
