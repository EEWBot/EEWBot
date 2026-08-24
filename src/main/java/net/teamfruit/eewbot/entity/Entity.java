package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public interface Entity {

    List<PendingComponent> createComponents(String lang, ComponentContext ctx, Supplier<IComponentBuilder> factory);

    private List<List<PendingComponent>> pack(final String lang, final ComponentContext ctx) {
        return ComponentPacker.pack(createComponents(lang, ctx, () -> new I18nComponentBuilder(lang, ctx.i18n())));
    }

    default List<MessageCreateSpec> createMessages(final String lang, final ComponentContext ctx) {
        final List<List<PendingComponent>> messages = pack(lang, ctx);
        final List<MessageCreateSpec> specs = new ArrayList<>(messages.size());
        for (final List<PendingComponent> message : messages)
            specs.add(MessageCreateSpec.builder().addAllComponents(ComponentRenderer.toDiscord4J(message)).build());
        return specs;
    }

    default List<DiscordWebhook> createWebhooks(final String lang, final ComponentContext ctx) {
        final List<List<PendingComponent>> messages = pack(lang, ctx);
        final List<DiscordWebhook> webhooks = new ArrayList<>(messages.size());
        for (final List<PendingComponent> message : messages)
            webhooks.add(DiscordWebhook.builder().components(ComponentRenderer.toWebhook(message)).build());
        return webhooks;
    }
}
