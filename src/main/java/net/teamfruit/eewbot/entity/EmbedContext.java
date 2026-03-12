package net.teamfruit.eewbot.entity;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;

public record EmbedContext(RendererQueryFactory renderer, QuakeInfoStore store, I18n i18n) {
}
