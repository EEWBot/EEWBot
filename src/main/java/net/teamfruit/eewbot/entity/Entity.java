package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.i18n.I18nDiscordEmbed;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

public interface Entity {

    <T> T createEmbed(String lang, I18n i18n, IEmbedBuilder<T> builder);

    default MessageCreateSpec createMessage(String lang, I18n i18n) {
        return MessageCreateSpec.builder().addEmbed(createEmbed(lang, i18n, I18nEmbedCreateSpec.builder(lang, i18n))).build();
    }

    default DiscordWebhook createWebhook(String lang, I18n i18n) {
        return DiscordWebhook.builder().addEmbed(createEmbed(lang, i18n, I18nDiscordEmbed.builder(lang, i18n))).build();
    }
}
