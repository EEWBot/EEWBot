package net.teamfruit.eewbot.entity;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.IntensityEmojiRegistry;

public record EmbedContext(RendererQueryFactory renderer, QuakeInfoStore store, I18n i18n,
                           IntensityEmojiRegistry intensityEmojis) {

    public EmbedContext(RendererQueryFactory renderer, QuakeInfoStore store, I18n i18n) {
        this(renderer, store, i18n, IntensityEmojiRegistry.empty());
    }
}
