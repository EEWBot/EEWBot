package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.i18n.I18nDiscordEmbed;
import net.teamfruit.eewbot.i18n.I18nEmbedCreateSpec;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

public interface Entity {

    <T> T createEmbed(String lang, EmbedContext ctx, IEmbedBuilder<T> builder);

    default MessageCreateSpec createMessage(String lang, EmbedContext ctx) {
        return MessageCreateSpec.builder().addEmbed(createEmbed(lang, ctx, I18nEmbedCreateSpec.builder(lang, ctx.i18n()))).build();
    }

    default DiscordWebhook createWebhook(String lang, EmbedContext ctx) {
        return DiscordWebhook.builder().addEmbed(createEmbed(lang, ctx, I18nDiscordEmbed.builder(lang, ctx.i18n()))).build();
    }
}
