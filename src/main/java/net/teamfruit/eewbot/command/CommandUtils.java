package net.teamfruit.eewbot.command;

import java.awt.Color;

import discord4j.core.spec.EmbedCreateSpec;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.Permission;

public class CommandUtils {

	public static boolean userHasPermission(final long userid, final String command) {
		return EEWBot.instance.getPermissions().values().stream()
				.filter(permission -> permission.getUserid().contains(userid))
				.findAny().orElse(EEWBot.instance.getPermissions().getOrDefault("everyone", Permission.DEFAULT_EVERYONE))
				.getCommand().contains(command);
	}

	public static EmbedCreateSpec createBaseEmbed(final EmbedCreateSpec embed) {
		return embed.setColor(new Color(7506394))
				.setAuthor(EEWBot.instance.getUsername(), "https://github.com/Team-Fruit/EEWBot", EEWBot.instance.getAvatarUrl())
				.setFooter("Team-Fruit/EEWBot", "http://i.imgur.com/gFHBoZA.png");
	}

	public static EmbedCreateSpec createBaseErrorEmbed(final EmbedCreateSpec embed) {
		return embed.setColor(new Color(255, 64, 64))
				.setAuthor(EEWBot.instance.getUsername(), "https://github.com/Team-Fruit/EEWBot", EEWBot.instance.getAvatarUrl())
				.setFooter("Team-Fruit/EEWBot", "http://i.imgur.com/gFHBoZA.png");
	}
}
