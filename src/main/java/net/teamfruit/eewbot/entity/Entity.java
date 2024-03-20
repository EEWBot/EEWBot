package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateSpec;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;

public interface Entity {

    MessageCreateSpec createMessage(String lang);

    DiscordWebhook createWebhook(String lang);
}
