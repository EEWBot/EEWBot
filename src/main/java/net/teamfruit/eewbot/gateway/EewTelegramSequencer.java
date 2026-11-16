package net.teamfruit.eewbot.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Decides which EEW telegrams are accepted and in what relation to earlier ones:
 * duplicate suppression across sockets, stale-announcement filtering, per-event latest state,
 * delivery history and concurrent-event numbering.
 * <p>
 * Independent of how telegrams arrive; the gateway only feeds decoded telegrams in.
 */
final class EewTelegramSequencer {

    private final Cache<String, EEWState> eventStates;
    private final Cache<String, Integer> maximumAnnouncementSerials;
    private final Cache<String, Boolean> receivedTelegramIds;
    private final EewConcurrentNumbering concurrentNumbering;

    private record EEWState(EEW eew, int serialNo, SeismicIntensity maxIntensitySoFar,
                            DmdataEEWUpdate.DeliveryHistory deliveryHistory, boolean finalReceived, boolean cancelled) {
        boolean isActive() {
            // Completion history stays terminal even if later ordinary telegrams are delivered.
            return !this.finalReceived && !this.cancelled;
        }
    }

    EewTelegramSequencer(Ticker ticker) {
        this.concurrentNumbering = new EewConcurrentNumbering(ticker);
        this.eventStates = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000)
                .ticker(ticker)
                .build();
        // Keep ordinary-report ordering independent of display/delivery state.
        // Only non-stale announcements renew this history; administrative reports cannot reset it.
        this.maximumAnnouncementSerials = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000)
                .ticker(ticker)
                .build();
        this.receivedTelegramIds = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(100_000)
                .ticker(ticker)
                .build();
    }

    /**
     * Cheap pre-check that lets callers skip body decoding for committed IDs.
     * {@link #accept} still checks atomically when concurrent arrivals both pass this
     * before the first one commits.
     */
    boolean isCommittedTelegramId(@Nullable String telegramId) {
        return StringUtils.isNotBlank(telegramId) && this.receivedTelegramIds.getIfPresent("id:" + telegramId) != null;
    }

    /**
     * Serializes acceptance and sink submission, including active-event numbering across sockets.
     * <p>
     * Success means the sink has classified the telegram and enqueued delivery (or intentionally
     * filtered it). Numbering, delivery state and the ID are not committed if the sink throws.
     * Actual Discord/external-webhook delivery is asynchronous and outside this guarantee.
     */
    synchronized void accept(@Nullable String telegramId, EEW eew, String xml, Consumer<DmdataEEWUpdate> sink) {
        String telegramKey = telegramKey(telegramId, xml);
        // Read without rewriting duplicates, so retransmission does not renew the TTL.
        if (this.receivedTelegramIds.getIfPresent(telegramKey) != null)
            return;
        int serialNo = Integer.parseInt(eew.getSerial());
        if (eew.getInfoType() == JMAInfoType.発表) {
            Integer maximumSerial = this.maximumAnnouncementSerials.getIfPresent(eew.getEventId());
            if (maximumSerial != null && serialNo < maximumSerial) {
                Log.logger.debug("Skipping older DMDATA EEW announcement: event {}, serial {} < {}",
                        eew.getEventId(), serialNo, maximumSerial);
                this.receivedTelegramIds.put(telegramKey, true);
                return;
            }
            // Receipt ordering is independent of enqueue success. Equal serials can still retry.
            this.maximumAnnouncementSerials.put(eew.getEventId(), serialNo);
        }
        EEWState state = this.eventStates.getIfPresent(eew.getEventId());
        EEW previous = state != null ? state.eew() : null;
        SeismicIntensity maxBefore = state != null ? state.maxIntensitySoFar() : SeismicIntensity.UNKNOWN;
        DmdataEEWUpdate.DeliveryHistory history = state != null ? state.deliveryHistory() : DmdataEEWUpdate.DeliveryHistory.EMPTY;
        DmdataEEWUpdate update = new DmdataEEWUpdate(eew, previous, maxBefore, history);
        SeismicIntensity runningMax = update.maxIntensityEEW();
        if (!eew.isCancelReport() && update.isDeliveryTarget()) {
            // Accumulate delivery history even when this telegram does not replace the latest state.
            history = history.include(eew.isEEWWarning(), runningMax);
        }
        boolean latest = state == null || replacesLatest(eew, serialNo, state);
        EEWState next = new EEWState(latest ? eew : previous, latest ? serialNo : state.serialNo(), runningMax, history,
                state != null && state.finalReceived() || latest && !eew.isCancelReport() && eew.isLastInfo(),
                state != null && state.cancelled() || latest && eew.isCancelReport());
        EewConcurrentNumbering.Plan numbering = this.concurrentNumbering.prepare(eew.getEventId(), next.isActive(), id -> {
            EEWState activeState = this.eventStates.getIfPresent(id);
            return activeState != null && activeState.isActive();
        });
        eew.setConcurrentIndex(numbering.number().index());
        eew.setConcurrent(numbering.number().everConcurrent());

        sink.accept(new DmdataEEWUpdate(eew, previous, maxBefore, history));
        this.concurrentNumbering.commit(numbering);
        this.eventStates.put(eew.getEventId(), next);
        this.receivedTelegramIds.put(telegramKey, true);
    }

    private static String telegramKey(@Nullable String id, String xml) {
        if (StringUtils.isNotBlank(id))
            return "id:" + id;
        // Only missing IDs use a body hash; keep its namespace separate from DMDATA IDs.
        try {
            return "xml:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static boolean replacesLatest(EEW current, int serialNo, EEWState state) {
        EEW previous = state.eew();
        int timeOrder = Comparator.<Instant>nullsFirst(Comparator.naturalOrder())
                .compare(current.getReportDateTime(), previous.getReportDateTime());
        if (timeOrder < 0)
            return false;
        // Equal timestamps can contain successive reports; a higher serial takes precedence
        // over the previous telegram's InfoType, including corrections and cancellations.
        if (timeOrder == 0 && serialNo > state.serialNo())
            return true;
        int typeOrder = Integer.compare(infoTypePriority(current.getInfoType()), infoTypePriority(previous.getInfoType()));
        if (timeOrder == 0 && typeOrder < 0)
            return false;
        // Later corrections/cancellations may reuse or lower the serial. With equal times and
        // no serial increase, prefer cancellation, correction, announcement, then delayed reports.
        boolean administrative = current.getInfoType() == JMAInfoType.訂正 || current.isCancelReport();
        if (administrative && (timeOrder > 0 || typeOrder > 0))
            return true;
        return serialNo >= state.serialNo();
    }

    private static int infoTypePriority(@Nullable JMAInfoType type) {
        if (type == null)
            return -1;
        return switch (type) {
            case 遅延 -> 0;
            case 発表 -> 1;
            case 訂正 -> 2;
            case 取消 -> 3;
        };
    }
}
