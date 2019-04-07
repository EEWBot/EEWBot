package net.teamfruit.eewbot.entity;

import java.util.function.Consumer;

import discord4j.core.spec.MessageCreateSpec;

public interface Entity {

	Consumer<? super MessageCreateSpec> createMessage();
}
