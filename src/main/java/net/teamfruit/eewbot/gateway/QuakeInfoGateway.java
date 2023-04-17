package net.teamfruit.eewbot.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.teamfruit.eewbot.entity.DetailQuakeInfo;
import net.teamfruit.eewbot.entity.QuakeInfo;
import net.teamfruit.eewbot.entity.QuakeInfo.Record.Item;

import javax.xml.bind.JAXB;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

public abstract class QuakeInfoGateway implements Gateway<DetailQuakeInfo> {

    public static final String REMOTE_ROOT = "https://www3.nhk.or.jp/sokuho/jishin/";
    public static final String REMOTE = "data/JishinReport.xml";

    private List<String> prev;

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-quakeinfo-thread");

            QuakeInfo quakeInfo = QuakeInfo.QUAKE_INFO_MAPPER.readValue(new URL(REMOTE_ROOT + REMOTE), QuakeInfo.class);

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
                    final DetailQuakeInfo detailQuakeInfo = DetailQuakeInfo.DETAIL_QUAKE_INFO_MAPPER.readValue(new URL(url), DetailQuakeInfo.class);
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
