package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;

public interface Entity {

	MessageCreateSpec createMessage(String lang);
}
