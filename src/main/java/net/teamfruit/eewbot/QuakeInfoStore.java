package net.teamfruit.eewbot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE51;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;
import org.jetbrains.annotations.Nullable;

import java.time.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class QuakeInfoStore {

    private final Cache<String, Map<JMAXmlType, History>> reports;
    private final AtomicReference<Instant> completeSince;
    private @Nullable QuakeInfo lastReport;

    private static class History {
        private QuakeInfo latest;
        private QuakeInfo normal;
        private SeismicIntensity referenceMax;
        private SeismicIntensity deliveryMax;
        private boolean deliveryStarted;
        private boolean complete;
        private int serial;
        private String reason = "no accepted delivery history";
    }

    public record DeliveryHistory(@Nullable SeismicIntensity maxIntensity, boolean complete, String reason) {
    }

    public QuakeInfoStore() {
        this(Clock.systemUTC(), Ticker.systemTicker());
    }

    public QuakeInfoStore(Clock clock, Ticker ticker) {
        this(clock, ticker, 10_000);
    }

    QuakeInfoStore(Clock clock, Ticker ticker, long maximumSize) {
        this.completeSince = new AtomicReference<>(clock.instant());
        this.reports = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(maximumSize)
                .ticker(ticker)
                // After any eviction, a newly recreated history cannot certify older events.
                .evictionListener((String key, Map<JMAXmlType, History> value, com.github.benmanes.caffeine.cache.RemovalCause cause) ->
                        this.completeSince.accumulateAndGet(clock.instant(), (a, b) -> a.isAfter(b) ? a : b))
                .build();
    }

    // Reference loading (including the long feed) never establishes delivery completeness.
    public synchronized void putReport(QuakeInfo report) {
        report.initQuakeInfoStore(this);
        JMAReport jma = (JMAReport) report;
        Map<JMAXmlType, History> event = event(report.getEventId());
        History history = event.computeIfAbsent(JMAXmlType.from(jma.getClass()), key -> new History());
        if (isNewer(report, history.latest))
            history.latest = report;
        if (!jma.isCancelReport()) {
            if (isNewer(report, history.normal))
                history.normal = report;
            history.referenceMax = max(history.referenceMax, report.getQuakeInfoMaxInt().orElse(null));
        }
        this.reports.put(report.getEventId(), event);
        if (isNewer(report, this.lastReport))
            this.lastReport = report;
    }

    // Called only after the message executor has accepted the ordinary report.
    public synchronized void recordDelivery(QuakeInfo report, @Nullable SeismicIntensity intensity) {
        JMAReport jma = (JMAReport) report;
        if (jma.isCancelReport())
            return;
        Map<JMAXmlType, History> event = event(report.getEventId());
        History history = event.computeIfAbsent(JMAXmlType.from(jma.getClass()), key -> new History());
        int serial = serial(jma);
        if (!history.deliveryStarted) {
            history.deliveryStarted = true;
            history.complete = eventStartedHere(report) && serial == 1 && jma.getInfoType() == JMAInfoType.発表;
            history.reason = history.complete ? "observed event from first report" : "event predates uninterrupted history or first report is missing";
        } else if (serial < 1 || serial > history.serial + 1) {
            history.complete = false;
            history.reason = "missing or invalid delivery serial";
        }
        if (intensity == null || intensity == SeismicIntensity.UNKNOWN) {
            history.complete = false;
            history.reason = "accepted report had no reliable intensity";
        }
        history.serial = Math.max(history.serial, serial);
        history.deliveryMax = max(history.deliveryMax, intensity);
        this.reports.put(report.getEventId(), event);
    }

    public synchronized DeliveryHistory getDeliveryHistory(String eventId, JMAXmlType type) {
        return history(eventId, type)
                .map(h -> new DeliveryHistory(h.deliveryMax, h.complete, h.reason))
                .orElseGet(() -> new DeliveryHistory(null, false, "event history missing or evicted"));
    }

    // Return the latest usable ordinary/corrected report, retaining it across cancellations.
    public synchronized Optional<QuakeInfo> getReport(String eventId, JMAXmlType type) {
        return history(eventId, type).map(h -> h.normal);
    }

    public synchronized Optional<QuakeInfo> getLatestReport(String eventId, JMAXmlType type) {
        return history(eventId, type).map(h -> h.latest);
    }

    public synchronized Optional<SeismicIntensity> getMaxIntensity(String eventId, JMAXmlType type) {
        return history(eventId, type).map(h -> h.referenceMax);
    }

    public synchronized Optional<QuakeInfo> getLatestReport() {
        return Optional.ofNullable(this.lastReport);
    }

    void cleanUp() {
        this.reports.cleanUp();
    }

    private Map<JMAXmlType, History> event(String eventId) {
        Map<JMAXmlType, History> event = this.reports.getIfPresent(eventId);
        return event != null ? event : new EnumMap<>(JMAXmlType.class);
    }

    private Optional<History> history(String eventId, JMAXmlType type) {
        return Optional.ofNullable(this.reports.getIfPresent(eventId)).map(event -> event.get(type));
    }

    private boolean eventStartedHere(QuakeInfo report) {
        try {
            Instant origin = null;
            if (report instanceof VXSE51 intensityReport)
                origin = intensityReport.getTargetDateTime();
            else if (report instanceof JmxSeis seis && seis.getBody() != null
                    && seis.getBody().getEarthquakes() != null && seis.getBody().getEarthquakes().size() == 1)
                origin = seis.getBody().getEarthquakes().getFirst().getOriginTime();
            JMAReport jma = (JMAReport) report;
            return origin != null && origin.isAfter(this.completeSince.get()) && jma.getReportDateTime() != null
                    && !jma.getReportDateTime().isBefore(origin);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isNewer(QuakeInfo candidate, @Nullable QuakeInfo previous) {
        if (previous == null)
            return true;
        JMAReport current = (JMAReport) candidate;
        JMAReport old = (JMAReport) previous;
        int comparison = compare(current.getReportDateTime(), old.getReportDateTime());
        if (comparison == 0)
            comparison = compare(current.getDateTime(), old.getDateTime());
        if (comparison == 0)
            comparison = Integer.compare(serial(current), serial(old));
        if (comparison == 0)
            comparison = Integer.compare(infoTypePriority(current), infoTypePriority(old));
        return comparison > 0;
    }

    private static int infoTypePriority(JMAReport report) {
        return report.isCancelReport() ? 2 : report.getInfoType() == JMAInfoType.訂正 ? 1 : 0;
    }

    private static int compare(@Nullable Instant current, @Nullable Instant old) {
        if (current == null)
            return old == null ? 0 : -1;
        return old == null ? 1 : current.compareTo(old);
    }

    private static int serial(JMAReport report) {
        try {
            return Integer.parseInt(report.getSerial());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static SeismicIntensity max(@Nullable SeismicIntensity a, @Nullable SeismicIntensity b) {
        return a == null || b != null && b.getCode() > a.getCode() ? b : a;
    }
}
