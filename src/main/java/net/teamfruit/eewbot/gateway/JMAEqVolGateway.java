package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.jma.JMAEqVol;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

public abstract class JMAEqVolGateway implements Gateway<JMAEqVolGateway> {

    public static final String REMOTE_ROOT = "https://www.data.jma.go.jp/developer/xml/feed/";
    public static final String REMOTE = "eqvol.xml";

    private List<String> prev;

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-jmaxml-thread");

            JMAEqVol eqVol = EEWBot.XML_MAPPER.readValue(new URL(REMOTE_ROOT + REMOTE), JMAEqVol.class);

            if (this.prev != null) {
                final List<String> list = eqVol.getEntries().stream()
                        .map(JMAEqVol.Entry::getId)
                        .collect(Collectors.toList());
                final List<String> newer = new ArrayList<>(list);
                newer.removeAll(this.prev);
                this.prev = list;

                for (final ListIterator<String> it = newer.listIterator(newer.size()); it.hasPrevious(); ) {
                    Log.logger.info("New JMA XML Activity: " + it.previous());
                }
            } else
                this.prev = eqVol.getEntries().stream()
                        .map(JMAEqVol.Entry::getId)
                        .collect(Collectors.toList());
        } catch (final Exception e) {
            onError(new EEWGatewayException(e));
        }
    }
}
