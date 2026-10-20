package net.teamfruit.eewbot.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Numbering metadata only; access and prepare/commit are serialized by DmdataGateway. */
final class EewConcurrentNumbering {

    private final Cache<String, EventNumber> history;
    // Active assignments must not be evicted by the bounded history cache.
    private Map<String, EventNumber> activeEvents = new HashMap<>();
    private @Nullable Group group;
    private long nextGroupId = 1;

    EewConcurrentNumbering(Ticker ticker) {
        this(ticker, 10_000);
    }

    EewConcurrentNumbering(Ticker ticker, long maximumHistorySize) {
        this.history = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(maximumHistorySize)
                .ticker(ticker)
                .build();
    }

    record EventNumber(long groupId, int index, boolean everConcurrent, boolean ended) {
        EventNumber concurrent() {
            return new EventNumber(this.groupId, this.index, true, this.ended);
        }

        EventNumber finish() {
            return new EventNumber(this.groupId, this.index, this.everConcurrent, true);
        }
    }

    private record Group(long id, int nextIndex) {
    }

    record Plan(EventNumber number, Map<String, EventNumber> activeEvents,
                Map<String, EventNumber> retiredEvents, @Nullable Group group, long nextGroupId) {
    }

    Plan prepare(String eventId, boolean active, Predicate<String> hasActiveState) {
        Map<String, EventNumber> nextActive = new HashMap<>(this.activeEvents);
        Map<String, EventNumber> retired = new HashMap<>();
        // Inspect only active IDs. Cache expiry/eviction closes activity without losing assignments.
        nextActive.entrySet().removeIf(entry -> {
            if (hasActiveState.test(entry.getKey()))
                return false;
            retired.put(entry.getKey(), entry.getValue());
            return true;
        });
        Group nextGroup = nextActive.isEmpty() ? null : this.group;
        long groupId = this.nextGroupId;
        EventNumber number = nextActive.get(eventId);
        if (number == null)
            number = retired.get(eventId);
        if (number == null)
            number = this.history.getIfPresent(eventId);

        if (number == null) {
            if (active) {
                if (nextGroup == null)
                    nextGroup = new Group(groupId++, 1);
                boolean concurrent = !nextActive.isEmpty();
                number = new EventNumber(nextGroup.id(), nextGroup.nextIndex(), concurrent, false);
                nextGroup = new Group(nextGroup.id(), nextGroup.nextIndex() + 1);
                if (concurrent)
                    nextActive.replaceAll((id, existing) -> existing.concurrent());
                nextActive.put(eventId, number);
            } else {
                // A terminal-only event does not establish overlap with the current group.
                number = new EventNumber(groupId++, 1, false, true);
                retired.put(eventId, number);
            }
        } else if (!active) {
            if (!number.ended()) {
                number = number.finish();
                retired.put(eventId, number);
            }
            nextActive.remove(eventId);
        }
        // Archived IDs keep their original group, even after activity times out. A delayed
        // report must never reopen a closed group or join a different active group.
        if (nextActive.isEmpty())
            nextGroup = null;
        return new Plan(number, nextActive, retired, nextGroup, groupId);
    }

    void commit(Plan plan) {
        this.history.putAll(plan.retiredEvents());
        this.activeEvents = plan.activeEvents();
        this.group = plan.group();
        this.nextGroupId = plan.nextGroupId();
    }
}
