package net.teamfruit.eewbot.slashcommand;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import reactor.core.publisher.Mono;

public class SlashCommandUtils {

    public static String getLanguage(EEWBot bot, InteractionCreateEvent event) {
        Channel channel = bot.getAdminRegistry().get(event.getInteraction().getChannelId().asLong());
        if (channel == null)
            return bot.getConfig().getBase().getDefaultLanguage();
        return channel.getLang();
    }

    public static Mono<Void> replyOrFollowUp(DeferrableInteractionEvent event, boolean defer, EmbedCreateSpec spec) {
        if (defer)
            return event.createFollowup().withEmbeds(spec)
                    .doOnError(err -> Log.logger.error("Error during follow-up message", err))
                    .then();
        return event.reply().withEmbeds(spec)
                .doOnError(err -> Log.logger.error("Error during reply", err));
    }

    public static IEmbedBuilder<EmbedCreateSpec> createEmbed(final String lang, final EEWBot bot) {
        return I18nEmbedCreateSpec.builder(lang, bot.getI18n())
                .color(Color.of(7506394))
                .author(bot.getUsername(), "https://github.com/EEWBot/EEWBot", bot.getAvatarUrl())
                .footer("EEWBot/EEWBot", "http://i.imgur.com/gFHBoZA.png");
    }

    public static IEmbedBuilder<EmbedCreateSpec> createErrorEmbed(final String lang, final EEWBot bot) {
        return I18nEmbedCreateSpec.builder(lang, bot.getI18n())
                .color(Color.of(255, 64, 64))
                .author(bot.getUsername(), "https://github.com/EEWBot/EEWBot", bot.getAvatarUrl())
                .footer("EEWBot/EEWBot", "http://i.imgur.com/gFHBoZA.png");
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
