package net.teamfruit.eewbot.gateway;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

import javax.xml.bind.JAXB;

import net.teamfruit.eewbot.entity.DetailQuakeInfo;
import net.teamfruit.eewbot.entity.QuakeInfo;
import net.teamfruit.eewbot.entity.QuakeInfo.Record.Item;

public abstract class QuakeInfoGateway implements Gateway<DetailQuakeInfo> {

	public static final String REMOTE_ROOT = "https://www3.nhk.or.jp/sokuho/jishin/";
	public static final String REMOTE = "data/JishinReport.xml";

	private List<String> prev;

	@Override
	public void run() {
		try {
			Thread.currentThread().setName("eewbot-quakeinfo-thread");

			final QuakeInfo quakeInfo = JAXB.unmarshal(new URL(REMOTE_ROOT+REMOTE), QuakeInfo.class);

			if (this.prev!=null) {
				final List<String> list = quakeInfo.getRecords().stream()
						.flatMap(record -> record.getItems().stream())
						.map(Item::getUrl)
						.collect(Collectors.toList());
				final List<String> newer = new ArrayList<>(list);
				newer.removeAll(this.prev);
				this.prev = list;

				for (final ListIterator<String> it = newer.listIterator(newer.size()); it.hasPrevious();) {
					final String url = it.previous();
					final DetailQuakeInfo detailQuakeInfo = JAXB.unmarshal(new URL(url), DetailQuakeInfo.class);
					onNewData(detailQuakeInfo);
				}
			} else
				this.prev = quakeInfo.getRecords().stream()
						.flatMap(record -> record.getItems().stream())
						.map(Item::getUrl)
						.collect(Collectors.toList());
		} catch (final Exception e) {
			onError(e);
		}
	}

}
