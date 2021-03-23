package net.teamfruit.eewbot;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import net.teamfruit.eewbot.registry.Channel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class EEWService {

	private final GatewayDiscordClient gateway;
	private final Map<Long, Channel> channels;
	private final Optional<TextChannel> systemChannel;

	public EEWService(final GatewayDiscordClient gateway, final Map<Long, Channel> map, final Optional<TextChannel> systemChannel) {
		this.gateway = gateway;
		this.channels = map;
		this.systemChannel = systemChannel;
	}

	public void sendMessage(final String key, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		sendMessage(channel -> channel.value(key), spec);
	}

	public void sendMessage(final Predicate<Channel> filter, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		Flux.merge(this.channels.entrySet().stream()
				.filter(entry -> filter.test(entry.getValue()))
				.map(entry -> directSendMessage(entry.getKey(), spec.apply(entry.getValue().lang)))
				.collect(Collectors.toList()))
				.parallel()
				.runOn(Schedulers.parallel())
				.groups()
				.subscribe(g -> g.subscribe(msg -> Log.logger.info(msg.getId().asString())));
	}

	public void sendAttachment(final String key, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		sendAttachment(channel -> channel.value(key), spec);
	}

	public void sendAttachment(final Predicate<Channel> filter, final Function<String, Consumer<? super MessageCreateSpec>> spec) {
		if (!this.systemChannel.isPresent())
			sendMessage(filter, spec);
		directSendMessage(this.systemChannel.get().getId().asLong(), spec.apply(null))
				.map(msg -> msg.getAttachments().iterator().next().getUrl())
				.subscribe(url -> sendMessage(filter, lang -> msg -> msg.setContent(url)));
	}

	private Mono<Message> directSendMessage(final long channelId, final Consumer<? super MessageCreateSpec> spec) {
		return Mono.defer(() -> {
			final MessageCreateSpec mutatedSpec = new MessageCreateSpec();
			this.gateway.getRestClient().getRestResources()
					.getAllowedMentions()
					.ifPresent(mutatedSpec::setAllowedMentions);
			spec.accept(mutatedSpec);
			return this.gateway.getRestClient().getChannelService()
					.createMessage(channelId, mutatedSpec.asRequest());
		})
				.map(data -> new Message(this.gateway, data))
				.doOnError(ClientException.class, err -> Log.logger.error("Failed to send message: ChannelID={} Message={}", channelId, err.getMessage()))
				.onErrorResume(e -> Mono.empty());
	}
}
