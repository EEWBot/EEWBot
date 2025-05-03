package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import net.teamfruit.eewbot.registry.channel.Channel;
import reactor.core.publisher.Mono;

public class SlashCommandUtils {

    public static String getLanguage(EEWBot bot, InteractionCreateEvent event) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        if (channel == null)
            return bot.getConfig().getDefaultLanguage();
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

    public static IEmbedBuilder<EmbedCreateSpec> createEmbed(final String lang) {
        return I18nEmbedCreateSpec.builder(lang)
                .color(Color.of(7506394))
                .author(EEWBot.instance.getUsername(), "https://github.com/EEWBot/EEWBot", EEWBot.instance.getAvatarUrl())
                .footer("EEWBot/EEWBot", "http://i.imgur.com/gFHBoZA.png");
    }

    public static IEmbedBuilder<EmbedCreateSpec> createErrorEmbed(final String lang) {
        return I18nEmbedCreateSpec.builder(lang)
                .color(Color.of(255, 64, 64))
                .author(EEWBot.instance.getUsername(), "https://github.com/EEWBot/EEWBot", EEWBot.instance.getAvatarUrl())
                .footer("EEWBot/EEWBot", "http://i.imgur.com/gFHBoZA.png");
    }
}
