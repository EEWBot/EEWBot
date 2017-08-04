package net.teamfruit.eewbot;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.teamfruit.eewbot.event.EEWEvent;
import net.teamfruit.eewbot.event.MonitorEvent;
import net.teamfruit.eewbot.event.QuakeInfoEvent;
import net.teamfruit.eewbot.node.EEW;
import net.teamfruit.eewbot.node.QuakeInfo;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.util.RequestBuffer;

public class EEWEventListener {

	@EventSubscriber
	public void onEEW(final EEWEvent e) {
		final EEW eew = e.getElement();
		for (final Iterator<Entry<Long, CopyOnWriteArrayList<Channel>>> it1 = EEWBot.instance.getChannels().entrySet().iterator(); it1.hasNext();) {
			final Entry<Long, CopyOnWriteArrayList<Channel>> entry = it1.next();
			for (final Iterator<Channel> it2 = entry.getValue().iterator(); it2.hasNext();) {
				final Channel channel = it2.next();
				if ((eew.isAlert()&&channel.eewAlert)||(!eew.isAlert()&&channel.eewPrediction)) {
					Optional.ofNullable(EEWBot.instance.getClient().getGuildByID(entry.getKey()))
							.ifPresent(id -> id.getChannelByID(channel.getId()).sendMessage(eew.buildEmbed()));
				}
			}
		}
	}

	@EventSubscriber
	public void onQuakeInfo(final QuakeInfoEvent e) {
		final QuakeInfo info = e.getElement();
		for (final Iterator<Entry<Long, CopyOnWriteArrayList<Channel>>> it1 = EEWBot.instance.getChannels().entrySet().iterator(); it1.hasNext();) {
			final Entry<Long, CopyOnWriteArrayList<Channel>> entry = it1.next();
			for (final Iterator<Channel> it2 = entry.getValue().iterator(); it2.hasNext();) {
				final Channel channel = it2.next();
				if (channel.quakeInfo) {
					final Optional<IGuild> guild = Optional.ofNullable(EEWBot.instance.getClient().getGuildByID(entry.getKey()));
					guild.ifPresent(id -> id.getChannelByID(channel.getId()).sendMessage(info.buildEmbed()));
					if (channel.quakeInfoDetail)
						info.getDetails().forEach(detail -> RequestBuffer.request(() -> guild.ifPresent(id -> id.getChannelByID(channel.getId()).sendMessage(detail.buildEmbed()))));
				}
			}
		}
	}

	@EventSubscriber
	public void onMonitor(final MonitorEvent e) {
		for (final Iterator<Entry<Long, CopyOnWriteArrayList<Channel>>> it1 = EEWBot.instance.getChannels().entrySet().iterator(); it1.hasNext();) {
			final Entry<Long, CopyOnWriteArrayList<Channel>> entry = it1.next();
			for (final Iterator<Channel> it2 = entry.getValue().iterator(); it2.hasNext();) {
				final Channel channel = it2.next();
				final IGuild id = EEWBot.instance.getClient().getGuildByID(entry.getKey());
				final IChannel c = id.getChannelByID(channel.getId());
				c.sendFile("", e.getElement(), "kyoshinmonitor.png");
			}
		}
	}
}
