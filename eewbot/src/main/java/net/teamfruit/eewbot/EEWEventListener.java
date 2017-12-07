package net.teamfruit.eewbot;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.teamfruit.eewbot.event.EEWEvent;
import net.teamfruit.eewbot.event.MonitorEvent;
import net.teamfruit.eewbot.event.QuakeInfoEvent;
import net.teamfruit.eewbot.node.EEW;
import net.teamfruit.eewbot.registry.Channel;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RequestBuffer;

public class EEWEventListener {

	@EventSubscriber
	public void onEEW(final EEWEvent e) {
		final EEW eew = e.getElement();
		final EEW prev = e.getPrev();
		final Predicate<Channel> isAlert = c -> c.getElement(eew.isAlert() ? "eewAlert" : "eewPrediction").get();
		final Predicate<Channel> decimation = c -> {
			if (!c.getElement("eewDecimation").get())
				return true;
			if (prev==null)
				return true;
			if (eew.isInitial()||eew.isFinal())
				return true;
			if (eew.isAlert()!=prev.isAlert())
				return true;
			if (!eew.getIntensity().equals(prev.getIntensity()))
				return true;
			if (!eew.getRegionName().equals(prev.getRegionName()))
				return true;
			return false;
		};
		action(isAlert.and(decimation), c -> c.sendMessage(e.getElement().buildEmbed()));
	}

	@EventSubscriber
	public void onQuakeInfo(final QuakeInfoEvent e) {
		if (!e.isDetailUpdate()) {
			final EmbedObject info = e.getElement().buildEmbed();
			action(c -> c.getElement("quakeInfo").get(), c -> c.sendMessage(info));
		}
		final List<EmbedObject> details = e.getElement().getDetailsEmbed();
		action(c -> c.getElement("quakeInfoDetail").get(), c -> details.forEach(d -> RequestBuffer.request(() -> c.sendMessage(d))));
	}

	@EventSubscriber
	public void onMonitor(final MonitorEvent e) {
		action(c -> c.getElement("monitor").get(), c -> c.sendFile("", new ByteArrayInputStream(e.getElement()), "kyoshinmonitor.png"));
	}

	public static void action(final Predicate<Channel> filter, final Consumer<IChannel> a) {
		EEWBot.instance.getChannels().entrySet()
				.forEach(entry -> {
					final IGuild guild = EEWBot.instance.getClient().getGuildByID(entry.getKey());
					if (guild!=null)
						entry.getValue().stream().filter(filter)
								.forEach(channel -> {
									final IChannel dc = guild.getChannelByID(channel.id);
									if (dc!=null)
										try {
											a.accept(dc);
										} catch (final MissingPermissionsException ex) {
											EEWBot.LOGGER.warn("権限がありません: "+guild.getName()+" #"+dc.getName());
										}
								});
				});
	}

}
