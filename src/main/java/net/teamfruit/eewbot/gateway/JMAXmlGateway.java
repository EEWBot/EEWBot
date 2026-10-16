package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.JMAFeed;
import net.teamfruit.eewbot.entity.jma.JMAStatus;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@SuppressWarnings("NonAsciiCharacters")
public class JMAXmlGateway implements Gateway<AbstractJMAReport> {

    private static final String DEFAULT_REMOTE_ROOT = "https://www.data.jma.go.jp/developer/xml/feed/";
    private static final String REMOTE = "eqvol.xml";
    private static final int MAX_RECENT = 10_000;
    private static final int MAX_PENDING = 10_000;

    private final java.net.http.HttpClient httpClient;
    private final QuakeInfoStore store;
    private final Listener listener;
    private final String remoteRoot;
    private final Clock clock;
    private final Instant catchUpSince;

    // Published successes never expire. Keep a bounded tail for incomplete/reordered feeds.
    private Set<EntryKey> published = Set.of();
    private final Set<EntryKey> processed = new HashSet<>();
    private final Set<EntryKey> recent = new LinkedHashSet<>();
    private final Map<EntryKey, PendingEntry> pending = new LinkedHashMap<>();
    private String lastModified;
    private boolean initialized;

    private record EntryKey(String id, String updated) {
        static EntryKey of(JMAFeed.Entry entry) {
            return new EntryKey(entry.getId(), entry.getUpdated());
        }
    }

    private static class PendingEntry {
        private final JMAFeed.Entry entry;
        private int failures;
        private Instant retryAt = Instant.MIN;

        private PendingEntry(JMAFeed.Entry entry) {
            this.entry = entry;
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onNewData(AbstractJMAReport report);
    }

    public JMAXmlGateway(java.net.http.HttpClient httpClient, QuakeInfoStore store, Listener listener, String debugFeedRoot) {
        this(httpClient, store, listener, debugFeedRoot, Clock.systemUTC());
    }

    JMAXmlGateway(java.net.http.HttpClient httpClient, QuakeInfoStore store, Listener listener, String debugFeedRoot, Clock clock) {
        this.httpClient = httpClient;
        this.store = store;
        this.listener = listener;
        this.clock = clock;
        this.catchUpSince = clock.instant().minus(Duration.ofMinutes(10));
        String root = StringUtils.isNotEmpty(debugFeedRoot) ? debugFeedRoot : DEFAULT_REMOTE_ROOT;
        this.remoteRoot = Strings.CS.appendIfMissing(root, "/");
    }

    @Override
    public void onNewData(AbstractJMAReport data) {
        this.listener.onNewData(data);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("eewbot-jmaxml-thread");
        try {
            pollFeed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            onError(new EEWGatewayException(e));
        }
        // Retry independently of feed availability, including 304 and entries no longer published.
        for (var it = this.pending.entrySet().iterator(); it.hasNext(); ) {
            var item = it.next();
            PendingEntry pendingEntry = item.getValue();
            if (this.clock.instant().isBefore(pendingEntry.retryAt))
                continue;
            try {
                if (Thread.currentThread().isInterrupted())
                    return;
                processReport(pendingEntry.entry);
                markProcessed(item.getKey());
                it.remove();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                long delaySeconds = Math.min(3600, 60L << Math.min(pendingEntry.failures++, 6));
                pendingEntry.retryAt = this.clock.instant().plusSeconds(delaySeconds);
                Log.logger.warn("Failed to process JMA XML Report: {}; retry at {} (still published: {})",
                        item.getKey(), pendingEntry.retryAt, this.published.contains(item.getKey()), e);
            }
        }
    }

    private void pollFeed() throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(this.remoteRoot + REMOTE))
                .header("User-Agent", "eewbot").GET();
        if (this.lastModified != null)
            request.header("If-Modified-Since", this.lastModified);
        HttpResponse<InputStream> response = this.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() == 304)
                return;
            if (response.statusCode() != 200) {
                Log.logger.warn("Failed to fetch JMA XML: HTTP {}", response.statusCode());
                return;
            }
            JMAFeed feed = Codecs.XML_MAPPER.readValue(body, JMAFeed.class);
            List<JMAFeed.Entry> entries = feed.getEntries() == null ? List.of() : feed.getEntries();
            Set<EntryKey> current = new HashSet<>();
            entries.forEach(entry -> current.add(EntryKey.of(entry)));
            this.published = current;
            for (var it = this.processed.iterator(); it.hasNext(); ) {
                EntryKey key = it.next();
                if (!current.contains(key)) {
                    this.recent.add(key);
                    it.remove();
                }
            }
            for (var it = this.recent.iterator(); it.hasNext(); ) {
                EntryKey key = it.next();
                if (current.contains(key)) {
                    this.processed.add(key);
                    it.remove();
                }
            }
            trimRecent();
            // Atom feeds are newest first; process older reports before their corrections.
            for (var it = entries.listIterator(entries.size()); it.hasPrevious(); ) {
                JMAFeed.Entry entry = it.previous();
                EntryKey key = EntryKey.of(entry);
                if (this.processed.contains(key) || this.recent.contains(key) || this.pending.containsKey(key))
                    continue;
                if (!this.initialized && isBeforeCatchUp(entry)) {
                    markProcessed(key);
                } else {
                    this.pending.put(key, new PendingEntry(entry));
                }
            }
            int missing = 0;
            for (EntryKey key : this.pending.keySet()) {
                if (!current.contains(key))
                    missing++;
            }
            for (var it = this.pending.keySet().iterator(); missing > MAX_PENDING && it.hasNext(); ) {
                EntryKey key = it.next();
                if (!current.contains(key)) {
                    it.remove();
                    missing--;
                    Log.logger.error("JMA XML retry capacity exceeded; evicting unresolved unpublished entry: {}", key);
                }
            }
            this.initialized = true;
            this.lastModified = response.headers().firstValue("Last-Modified").orElse(null);
        }
    }

    private boolean isBeforeCatchUp(JMAFeed.Entry entry) {
        try {
            return OffsetDateTime.parse(entry.getUpdated()).toInstant().isBefore(this.catchUpSince);
        } catch (Exception e) {
            Log.logger.warn("JMA XML entry has no usable update time; including in catch-up: {}", entry);
            return false;
        }
    }

    private void markProcessed(EntryKey key) {
        if (this.published.contains(key))
            this.processed.add(key);
        else {
            this.recent.add(key);
            trimRecent();
        }
    }

    private void trimRecent() {
        while (this.recent.size() > MAX_RECENT)
            this.recent.remove(this.recent.iterator().next());
    }

    private void processReport(JMAFeed.Entry entry) throws Exception {
        if (entry.getTitle().isEmpty()) {
            Log.logger.warn("Unknown JMA XML Activity: {}", entry);
            return;
        }
        Class<? extends AbstractJMAReport> reportClass = entry.getTitle().get().getReportClass();
        if (reportClass == null)
            return;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(entry.getLink().getHref()))
                .header("User-Agent", "eewbot").GET().build();
        HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        String xml;
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200)
                throw new java.io.IOException("JMA XML report HTTP " + response.statusCode() + ": " + request.uri());
            xml = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
        AbstractJMAReport report = Codecs.XML_MAPPER.readValue(xml, reportClass);
        report.setRawData(xml);
        if (report.getControl().getStatus() == JMAStatus.通常) {
            if (report instanceof QuakeInfo quakeInfo)
                this.store.putReport(quakeInfo);
            Log.logger.info("JMA XML Report: {}", report);
            onNewData(report);
        }
    }
}
