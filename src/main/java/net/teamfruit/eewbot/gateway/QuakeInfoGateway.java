package net.teamfruit.eewbot.gateway;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import javax.xml.bind.JAXB;

import net.teamfruit.eewbot.entity.DetailQuakeInfo;
import net.teamfruit.eewbot.entity.QuakeInfo;
import net.teamfruit.eewbot.entity.QuakeInfo.Record;
import net.teamfruit.eewbot.entity.QuakeInfo.Record.Item;

public abstract class QuakeInfoGateway implements Gateway<DetailQuakeInfo> {

	public static final String REMOTE_ROOT = "https://www3.nhk.or.jp/sokuho/jishin/";
	public static final String REMOTE = "data/JishinReport.xml";

	private Optional<String> lastUrl = Optional.empty();

	@Override
	public void run() {
		try {
			final QuakeInfo quakeInfo = JAXB.unmarshal(new URL(REMOTE_ROOT+REMOTE), QuakeInfo.class);

			if (this.lastUrl.isPresent()) {
				final List<String> list = new ArrayList<>();

				records: for (final Record record : quakeInfo.getRecords())
					for (final Item item : record.getItems()) {
						if (this.lastUrl.get().equals(item.getUrl()))
							break records;
						list.add(item.getUrl());
					}

				if (!list.isEmpty())
					this.lastUrl = list.stream().findFirst();

				for (final ListIterator<String> it = list.listIterator(); it.hasPrevious();) {
					final String url = it.previous();
					final DetailQuakeInfo detailQuakeInfo = JAXB.unmarshal(new URL(url), DetailQuakeInfo.class);
					onNewData(detailQuakeInfo);
				}
			} else
				this.lastUrl = quakeInfo.getRecords().stream().findFirst()
						.flatMap(record -> record.getItems().stream().findFirst())
						.map(Item::getUrl);
		} catch (final Exception e) {
			onError(e);
		}
	}

}
