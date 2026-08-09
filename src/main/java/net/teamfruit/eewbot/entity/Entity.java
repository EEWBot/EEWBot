package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public interface Entity {

    List<PendingEmbed> createEmbeds(String lang, EmbedContext ctx, Supplier<IEmbedBuilder> factory);

    private List<List<PendingEmbed>> pack(final String lang, final EmbedContext ctx) {
        return EmbedPacker.pack(createEmbeds(lang, ctx, () -> new I18nEmbedBuilder(lang, ctx.i18n())));
    }

    default List<MessageCreateSpec> createMessages(final String lang, final EmbedContext ctx) {
        final List<List<PendingEmbed>> messages = pack(lang, ctx);
        final List<MessageCreateSpec> specs = new ArrayList<>(messages.size());
        for (final List<PendingEmbed> message : messages)
            specs.add(MessageCreateSpec.builder().addAllEmbeds(EmbedRenderer.toEmbedCreateSpecs(message)).build());
        return specs;
    }

    default List<DiscordWebhook> createWebhooks(final String lang, final EmbedContext ctx) {
        final List<List<PendingEmbed>> messages = pack(lang, ctx);
        final List<DiscordWebhook> webhooks = new ArrayList<>(messages.size());
        for (final List<PendingEmbed> message : messages)
            webhooks.add(DiscordWebhook.builder().embeds(EmbedRenderer.toDiscordEmbeds(message)).build());
        return webhooks;
    }
}
