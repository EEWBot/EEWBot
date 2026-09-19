package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;

import java.time.Instant;

public interface IComponentBuilder {

    IComponentBuilder heading(String key, Object... format);

    IComponentBuilder text(String key, Object... format);

    IComponentBuilder rawText(String text);

    IComponentBuilder detail(String labelKey, String value);

    IComponentBuilder detail(String labelKey, String valueKey, Object... format);

    IComponentBuilder separator();

    IComponentBuilder media(String url, String description);

    IComponentBuilder accent(int rgb);

    default IComponentBuilder accent(final Color color) {
        return accent(color.getRGB());
    }

    IComponentBuilder footer(String text);

    IComponentBuilder timestamp(Instant time);

    PendingComponent.Container build();
}
