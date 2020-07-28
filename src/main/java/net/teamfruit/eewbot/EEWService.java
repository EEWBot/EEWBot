package net.teamfruit.eewbot;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import net.teamfruit.eewbot.registry.Channel;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class EEWService {

	private final GatewayDiscordClient gateway;
	private final Map<Long, Channel> channels;

	public EEWService(final GatewayDiscordClient gateway, final Map<Long, Channel> map) {
		this.gateway = gateway;
		this.channels = map;
	}

	public Mono<Void> sendMessage(final String key, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		return sendMessage(channel -> channel.value(key), spec);
	}

	public Mono<Void> sendMessage(final Predicate<Channel> filter, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		return Mono.whenDelayError(this.channels.entrySet().stream()
				.filter(entry -> filter.test(entry.getValue()))
				.map(entry -> this.gateway.getChannelById(Snowflake.of(entry.getKey()))
						.filter(c -> c.getType()==discord4j.core.object.entity.channel.Channel.Type.GUILD_TEXT)
						.cast(TextChannel.class)
						.flatMap(tc -> tc.createMessage(spec.apply(entry.getValue().lang)).onErrorResume(t -> {
							if (t instanceof ClientException) {
								final ClientException ce = (ClientException) t;
								Log.logger.info(String.format("ClientException: GuildID=%s ChannelID=%s ChannelName=%s Message=%s",
										tc.getGuildId().asString(),
										tc.getId().asString(),
										tc.getName(),
										ce.getErrorResponse().map(r -> r.getFields().getOrDefault("message", "")).orElse("")));
							}
							return Mono.empty();
						})))
				.collect(Collectors.toList())).subscribeOn(Schedulers.parallel());
	}

}
