package net.teamfruit.eewbot;

import java.io.ByteArrayInputStream;
import java.util.List;

import net.teamfruit.eewbot.event.EEWEvent;
import net.teamfruit.eewbot.event.MonitorEvent;
import net.teamfruit.eewbot.event.QuakeInfoEvent;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RequestBuffer;

public class EEWEventListener {

	@EventSubscriber
	public void onEEW(final EEWEvent e) {
		final EmbedObject eew = e.getElement().buildEmbed();
		action(e.getElement().isAlert() ? "eewAlert" : "eewPrediction", c -> c.sendMessage(eew));
	}

	@EventSubscriber
	public void onQuakeInfo(final QuakeInfoEvent e) {
		if (!e.isDetailUpdate()) {
			final EmbedObject info = e.getElement().buildEmbed();
			action("quakeInfo", c -> c.sendMessage(info));
		}
		final List<EmbedObject> details = e.getElement().getDetailsEmbed();
		action("quakeInfoDetail", c -> details.forEach(d -> RequestBuffer.request(() -> c.sendMessage(d))));
	}

	@EventSubscriber
	public void onMonitor(final MonitorEvent e) {
		action("monitor", c -> c.sendFile("", new ByteArrayInputStream(e.getElement()), "kyoshinmonitor.png"));
	}

	public static void action(final String e, final ChannelAction a) {
		EEWBot.instance.getChannels().entrySet()
				.forEach(entry -> {
					final IGuild guild = EEWBot.instance.getClient().getGuildByID(entry.getKey());
					entry.getValue().stream()
							.filter(channel -> channel.getElement(e).get())
							.forEach(channel -> {
								if (guild!=null) {
									final IChannel dc = guild.getChannelByID(channel.id);
									try {
										a.action(dc);
									} catch (final MissingPermissionsException ex) {
										EEWBot.LOGGER.warn("権限がありません: "+guild.getName()+" #"+dc.getName());
									}
								}
							});
				});
	}

	public static interface ChannelAction {

		void action(IChannel channel);
	}
}
