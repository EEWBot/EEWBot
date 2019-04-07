package net.teamfruit.eewbot.entity;

import java.util.function.Consumer;

import discord4j.core.spec.EmbedCreateSpec;

public interface Entity {

	Consumer<? super EmbedCreateSpec> createEmbed();
}
