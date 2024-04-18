package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.jma.JMAFeed;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

public abstract class JMAXmlGateway implements Gateway<JMAFeed> {

    public static final String REMOTE_ROOT = "https://www.data.jma.go.jp/developer/xml/feed/";
    public static final String REMOTE = "eqvol.xml";

    private String lastModified;
    private List<String> lastIds;

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-jmaxml-thread");

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(REMOTE_ROOT + REMOTE))
                    .header("User-Agent", "eewbot")
                    .GET();
            if (this.lastModified != null) {
                request.header("If-Modified-Since", this.lastModified);
            }

            HttpResponse<InputStream> response = EEWBot.instance.getHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 304) {
                return;
            }
            if (response.statusCode() != 200) {
                Log.logger.warn("Failed to fetch JMA XML: HTTP " + response.statusCode());
                return;
            }

            JMAFeed feed = EEWBot.XML_MAPPER.readValue(new InputStreamReader(response.body()), JMAFeed.class);

            if (this.lastIds != null) {
                final List<String> list = feed.getEntries().stream()
                        .map(JMAFeed.Entry::getId)
                        .collect(Collectors.toList());
                final List<String> newer = new ArrayList<>(list);
                newer.removeAll(this.lastIds);
                this.lastIds = list;

                if (!newer.isEmpty()) {
                    for (final ListIterator<String> it = newer.listIterator(newer.size()); it.hasPrevious(); ) {
                        Log.logger.info("New JMA XML Activity: " + it.previous());
                    }
                }
            } else {
                this.lastIds = feed.getEntries().stream()
                        .map(JMAFeed.Entry::getId)
                        .collect(Collectors.toList());
            }

            this.lastModified = response.headers().firstValue("Last-Modified").orElseThrow();
        } catch (final Exception e) {
            onError(new EEWGatewayException(e));
        }
    }
}
