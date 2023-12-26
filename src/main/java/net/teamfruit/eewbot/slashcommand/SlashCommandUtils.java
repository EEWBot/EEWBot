package net.teamfruit.eewbot.slashcommand;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.util.Optional;

public class SlashCommandUtils {

    public static String getLanguage(final EEWBot bot, final Optional<Snowflake> guildId) {
//		return guildId.map(id -> bot.getGuilds().get(id.asLong()))
//				.map(guild -> guild.getLang())
//				.orElse(bot.getConfig().getDefaultLanuage());
        return bot.getConfig().getDefaultLanuage();
    }

    public static String getLanguage(final EEWBot bot, final MessageCreateEvent event) {
        return getLanguage(bot, event.getGuildId());
    }

    public static String getLanguage(final EEWBot bot, final ReactionAddEvent event) {
        return getLanguage(bot, event.getGuildId());
    }

    public static IEmbedBuilder<EmbedCreateSpec> createEmbed(final String lang) {
        return I18nEmbedCreateSpec.builder(lang)
                .color(Color.of(7506394))
                .author(EEWBot.instance.getUsername(), "https://github.com/Team-Fruit/EEWBot", EEWBot.instance.getAvatarUrl())
                .footer("Team-Fruit/EEWBot", "http://i.imgur.com/gFHBoZA.png");
    }

    public static IEmbedBuilder<EmbedCreateSpec> createErrorEmbed(final String lang) {
        return I18nEmbedCreateSpec.builder(lang)
                .color(Color.of(255, 64, 64))
                .author(EEWBot.instance.getUsername(), "https://github.com/Team-Fruit/EEWBot", EEWBot.instance.getAvatarUrl())
                .footer("Team-Fruit/EEWBot", "http://i.imgur.com/gFHBoZA.png");
    }
}
