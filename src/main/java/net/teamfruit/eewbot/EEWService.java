package net.teamfruit.eewbot;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
	private final Map<Long, List<Channel>> channels;

	public EEWService(final DiscordClient client, final Map<Long, List<Channel>> channels) {
		this.client = client;
		this.channels = channels;
	}

	public Mono<Void> sendMessage(final String key, final Consumer<? super MessageCreateSpec> spec) {
		return sendMessage(channel -> channel.getElement(key).get(), spec);
	}

	public Mono<Void> sendMessage(final Predicate<Channel> filter, final Consumer<? super MessageCreateSpec> spec) {
		return Mono.whenDelayError(this.channels.values().stream().flatMap(List::stream)
				.filter(filter::test)
				.map(channel -> this.client.getChannelById(Snowflake.of(channel.id))
						.filter(c -> c.getType()==discord4j.core.object.entity.Channel.Type.GUILD_TEXT)
						.cast(TextChannel.class)
						.flatMap(tc -> tc.createMessage(spec)))
				.collect(Collectors.toList()));
	}

}
