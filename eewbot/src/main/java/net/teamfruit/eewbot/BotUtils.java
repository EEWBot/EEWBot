package net.teamfruit.eewbot;

import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang3.EnumUtils;

import net.teamfruit.eewbot.DiscordEventListener.Command;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.RequestBuffer;

public class BotUtils {

	public static void reply(final MessageReceivedEvent e, final String message) {
		RequestBuffer.request(() -> e.getMessage().getChannel().sendMessage(message));
	}

	public static void reply(final MessageReceivedEvent e, final EmbedObject embed) {
		RequestBuffer.request(() -> e.getMessage().getChannel().sendMessage(embed));
	}

	public static void reply(final MessageReceivedEvent e, final String message, final EmbedObject embed) {
		RequestBuffer.request(() -> e.getMessage().getChannel().sendMessage(message, embed));
	}

	public static Channel getChannel(final long serverId, final long channelId) {
		final CopyOnWriteArrayList<Channel> channels = EEWBot.instance.getChannels().get(serverId);
		if (channels!=null)
			return channels.stream().filter(c -> c.id==channelId).findFirst().orElse(null);
		return null;
	}

	public static boolean userHasPermission(final long userid, final Command command) {
		return EEWBot.instance.getPermissions().values().stream()
				.filter(permission -> permission.getUserid().stream()
						.anyMatch(id -> id==userid))
				.findAny().orElse(EEWBot.instance.getPermissions().getOrDefault("everyone", Permission.DEFAULT_EVERYONE))
				.getCommand().stream()
				.map(str -> EnumUtils.getEnum(Command.class, str))
				.anyMatch(cmd -> cmd==command);
	}
}
