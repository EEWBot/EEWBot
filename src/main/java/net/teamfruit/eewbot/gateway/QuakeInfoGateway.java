package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.other.NHKDetailQuakeInfo;
import net.teamfruit.eewbot.entity.other.NHKQuakeInfo;
import net.teamfruit.eewbot.entity.other.NHKQuakeInfo.Record.Item;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

public abstract class QuakeInfoGateway implements Gateway<NHKDetailQuakeInfo> {

    public static final String REMOTE_ROOT = "https://www3.nhk.or.jp/sokuho/jishin/";
    public static final String REMOTE = "data/JishinReport.xml";

    private List<String> prev;

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-quakeinfo-thread");

            NHKQuakeInfo quakeInfo = EEWBot.XML_MAPPER.readValue(new URL(REMOTE_ROOT + REMOTE), NHKQuakeInfo.class);

            if (this.prev != null) {
                final List<String> list = quakeInfo.getRecords().stream()
                        .flatMap(record -> record.getItems().stream())
                        .map(Item::getUrl)
                        .collect(Collectors.toList());
                final List<String> newer = new ArrayList<>(list);
                newer.removeAll(this.prev);
                this.prev = list;

                for (final ListIterator<String> it = newer.listIterator(newer.size()); it.hasPrevious(); ) {
                    final String url = it.previous();
                    final NHKDetailQuakeInfo detailQuakeInfo = NHKDetailQuakeInfo.DETAIL_QUAKE_INFO_MAPPER.readValue(new URL(url), NHKDetailQuakeInfo.class);
                    onNewData(detailQuakeInfo);
                }
            } else
                this.prev = quakeInfo.getRecords().stream()
                        .flatMap(record -> record.getItems().stream())
                        .map(Item::getUrl)
                        .collect(Collectors.toList());
        } catch (final Exception e) {
            onError(new EEWGatewayException(e));
        }
    }

}
