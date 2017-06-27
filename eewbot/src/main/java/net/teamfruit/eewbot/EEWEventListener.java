package net.teamfruit.eewbot;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher.EEW;
import net.teamfruit.eewbot.dispatcher.EEWEvent;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.util.EmbedBuilder;

public class EEWEventListener {

	@EventSubscriber
	public void onEEW(final EEWEvent e) {
		final EEW eew = e.getEEW();
		for (final Iterator<Entry<Long, CopyOnWriteArrayList<Channel>>> it1 = EEWBot.channels.entrySet().iterator(); it1.hasNext();) {
			final Entry<Long, CopyOnWriteArrayList<Channel>> entry = it1.next();
			for (final Iterator<Channel> it2 = entry.getValue().iterator(); it2.hasNext();) {
				final Channel channel = it2.next();
				if ((eew.isAlert()&&channel.eewAlart)||(!eew.isAlert()&&channel.eewPrediction)) {
					final IGuild id = EEWBot.client.getGuildByID(entry.getKey());
					final IChannel c = id.getChannelByID(channel.getId());
					c.sendMessage(":warning: **緊急地震速報("+eew.getAlertFlg()+")** "+(eew.isFinal() ? "最終報" : "第"+eew.getReportNum()+"報"), buildEmbed(eew));
				}
			}
		}
	}

	public static EmbedObject buildEmbed(final EEW eew) {
		final EmbedBuilder builder = new EmbedBuilder();

		builder.appendField("震央", eew.getRegionName(), true);
		builder.appendField("深さ", String.valueOf(eew.getDepth()), true);
		builder.appendField("マグニチュード", String.valueOf(eew.getMagnitude()), true);
		builder.appendField("予想震度", String.valueOf(eew.getIntensity()), false);

		if (eew.isAlert())
			builder.withColor(255, 0, 0);
		else
			builder.withColor(0, 0, 255);
		builder.withTitle("緊急地震速報 ("+eew.getAlertFlg()+")");
		builder.withTimestamp(eew.getReportTime().getTime());

		builder.withFooterText("新強震モニタ");
		return builder.build();
	}
}
