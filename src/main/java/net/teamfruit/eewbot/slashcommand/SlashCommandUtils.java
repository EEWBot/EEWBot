package net.teamfruit.eewbot.slashcommand;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.component.TopLevelMessageComponent;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.discord.ComponentRenderer;
import net.teamfruit.eewbot.entity.discord.ContainerBuilder;
import net.teamfruit.eewbot.entity.discord.PendingComponent;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import reactor.core.publisher.Mono;

import java.util.List;

public class SlashCommandUtils {

    public static String getLanguage(SlashCommandContext ctx, InteractionCreateEvent event) {
        Channel channel = ctx.adminRegistry().get(event.getInteraction().getChannelId().asLong());
        if (channel == null)
            return ctx.config().getBase().getDefaultLanguage();
        return channel.getLang();
    }

    public static Mono<Void> replyOrFollowUp(DeferrableInteractionEvent event, boolean defer, List<TopLevelMessageComponent> components) {
        if (defer)
            return event.createFollowup().withComponents(components)
                    .doOnError(err -> Log.logger.error("Error during follow-up message", err))
                    .then();
        return event.reply().withComponents(components)
                .doOnError(err -> Log.logger.error("Error during reply", err));
    }

    public static ContainerBuilder createContainer() {
        return PendingComponent.Container.builder().accentColor(7506394);
    }

    public static ContainerBuilder createErrorContainer() {
        return PendingComponent.Container.builder().accentColor(0xff4040);
    }

    public static List<TopLevelMessageComponent> render(final ContainerBuilder builder) {
        return ComponentRenderer.toDiscord4J(List.of(builder.build()));
    }

    /**
     * Create a default Channel for the given guild channel (handling ThreadChannel)
     * and register it in the admin registry.
     */
    public static Channel createAndRegisterDefault(DestinationAdminRegistry registry, GuildChannel guildChannel, long targetId, Long guildId, String lang) {
        boolean isThreadChannel = guildChannel instanceof ThreadChannel;
        Long parentChannelId = isThreadChannel
                ? ((ThreadChannel) guildChannel).getParentId().map(Snowflake::asLong).orElse(null)
                : null;
        Channel newChannel = createDefaultChannelForTarget(targetId, guildId, lang, isThreadChannel, parentChannelId);
        registry.put(targetId, newChannel);
        return newChannel;
    }

    static Channel createDefaultChannelForTarget(long targetId, Long guildId, String lang, boolean isThreadChannel, Long parentChannelId) {
        if (isThreadChannel) {
            if (parentChannelId == null) {
                throw new IllegalStateException("Thread channel does not have a parentId");
            }
            return Channel.createDefault(guildId, parentChannelId, targetId, lang);
        }
        return Channel.createDefault(guildId, targetId, null, lang);
    }
}
