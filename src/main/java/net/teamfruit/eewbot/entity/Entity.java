package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.i18n.I18nDiscordEmbed;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

public interface Entity {

    <T> T createEmbed(String lang, IEmbedBuilder<T> builder);

    default MessageCreateSpec createMessage(String lang) {
        return MessageCreateSpec.builder().addEmbed(createEmbed(lang, I18nEmbedCreateSpec.builder(lang))).build();
    }

    default DiscordWebhook createWebhook(String lang) {
        return DiscordWebhook.builder().addEmbed(createEmbed(lang, I18nDiscordEmbed.builder(lang))).build();
    }
}
