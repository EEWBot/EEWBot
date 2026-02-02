package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of all delivery channels for zero-DB-access delivery path.
 * Thread-safe by immutability - can be safely swapped via AtomicReference.
 */
public final class DeliverySnapshot {

    /**
     * Immutable record representing a delivery destination.
     */
    public record DeliveryChannel(
            long targetId,
            long channelId,
            Long threadId,
            Long guildId,
            boolean eewAlert,
            boolean eewPrediction,
            boolean eewDecimation,
            boolean quakeInfo,
            SeismicIntensity minIntensity,
            String lang,
            ChannelWebhook webhook
    ) {
        /**
         * Check if this is a guild channel.
         */
        public boolean isGuild() {
            return this.guildId != null;
        }

        /**
         * Convert to ChannelBase for delivery.
         */
        public ChannelBase toChannelBase() {
            return new ChannelBase(this.guildId, this.channelId, this.threadId, this.webhook, this.lang);
        }
    }

    private final long revision;
    private final Map<Long, DeliveryChannel> byTargetId;

    // Pre-computed indexes for efficient lookup
    private final Map<Long, List<Long>> targetsByChannelId;
    private final List<Long> targetsWithWebhook;
    private final List<Long> targetsWithoutWebhook;

    public DeliverySnapshot(long revision, List<DeliveryChannel> channels) {
        this.revision = revision;

        // Build main lookup map
        Map<Long, DeliveryChannel> targetMap = new HashMap<>();
        for (DeliveryChannel channel : channels) {
            targetMap.put(channel.targetId(), channel);
        }
        this.byTargetId = Collections.unmodifiableMap(targetMap);

        // Build channel_id -> target_id index
        Map<Long, List<Long>> channelIndex = new HashMap<>();
        for (DeliveryChannel channel : channels) {
            channelIndex.computeIfAbsent(channel.channelId(), k -> new ArrayList<>())
                    .add(channel.targetId());
        }
        // Make inner lists immutable
        for (Map.Entry<Long, List<Long>> entry : channelIndex.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.targetsByChannelId = Collections.unmodifiableMap(channelIndex);

        // Build webhook presence partitions
        List<Long> withWebhook = new ArrayList<>();
        List<Long> withoutWebhook = new ArrayList<>();
        for (DeliveryChannel channel : channels) {
            if (channel.webhook() != null) {
                withWebhook.add(channel.targetId());
            } else {
                withoutWebhook.add(channel.targetId());
            }
        }
        this.targetsWithWebhook = Collections.unmodifiableList(withWebhook);
        this.targetsWithoutWebhook = Collections.unmodifiableList(withoutWebhook);
    }

    public long getRevision() {
        return this.revision;
    }

    public DeliveryChannel get(long targetId) {
        return this.byTargetId.get(targetId);
    }

    public int size() {
        return this.byTargetId.size();
    }

    /**
     * Get channels partitioned by webhook presence, applying the given filter.
     * Returns Map with true -> channels with webhook, false -> channels without webhook.
     */
    public Map<Boolean, Map<Long, ChannelBase>> getPartitionedByWebhook(ChannelFilter filter) {
        Predicate<DeliveryChannel> predicate = buildPredicate(filter);

        Map<Long, ChannelBase> withWebhook = new HashMap<>();
        Map<Long, ChannelBase> withoutWebhook = new HashMap<>();

        for (long targetId : this.targetsWithWebhook) {
            DeliveryChannel channel = this.byTargetId.get(targetId);
            if (predicate.test(channel)) {
                withWebhook.put(targetId, channel.toChannelBase());
            }
        }

        for (long targetId : this.targetsWithoutWebhook) {
            DeliveryChannel channel = this.byTargetId.get(targetId);
            if (predicate.test(channel)) {
                withoutWebhook.put(targetId, channel.toChannelBase());
            }
        }

        return Map.of(true, withWebhook, false, withoutWebhook);
    }

    /**
     * Execute action on all target IDs matching the filter.
     */
    public void actionOnChannels(ChannelFilter filter, Consumer<Long> consumer) {
        Predicate<DeliveryChannel> predicate = buildPredicate(filter);

        for (DeliveryChannel channel : this.byTargetId.values()) {
            if (predicate.test(channel)) {
                consumer.accept(channel.targetId());
            }
        }
    }

    /**
     * Get target IDs of channels without webhook matching the filter.
     */
    public List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
        if (filter == null) {
            return new ArrayList<>(this.targetsWithoutWebhook);
        }

        Predicate<DeliveryChannel> predicate = buildPredicate(filter);
        return this.targetsWithoutWebhook.stream()
                .filter(targetId -> predicate.test(this.byTargetId.get(targetId)))
                .collect(Collectors.toList());
    }

    /**
     * Get all target IDs of channels without webhook.
     */
    public List<Long> getWebhookAbsentChannels() {
        return new ArrayList<>(this.targetsWithoutWebhook);
    }

    /**
     * Check if a webhook is used only for a specific thread.
     */
    public boolean isWebhookForThread(long webhookId, long targetId) {
        for (long tid : this.targetsWithWebhook) {
            if (tid != targetId) {
                DeliveryChannel channel = this.byTargetId.get(tid);
                if (channel.webhook() != null && channel.webhook().getId() == webhookId) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Build a predicate from ChannelFilter for in-memory filtering.
     */
    private Predicate<DeliveryChannel> buildPredicate(ChannelFilter filter) {
        if (filter == null) {
            return ch -> true;
        }

        return ch -> {
            if (filter.isHasGuildPresent()) {
                Boolean filterHasGuild = filter.getHasGuild();
                if (filterHasGuild != null && filterHasGuild != ch.isGuild()) {
                    return false;
                }
            }

            if (filter.isGuildIdPresent() && (ch.guildId() == null || ch.guildId() != filter.getGuildId())) {
                return false;
            }

            if (filter.isChannelIdPresent() && ch.channelId() != filter.getChannelId()) {
                return false;
            }

            if (filter.isThreadIdPresent()) {
                Long filterThreadId = filter.getThreadId();
                if (filterThreadId == null) {
                    if (ch.threadId() != null) return false;
                } else if (!filterThreadId.equals(ch.threadId())) {
                    return false;
                }
            }

            if (filter.isIsThreadPresent()) {
                Boolean isThread = filter.getIsThread();
                boolean channelIsThread = ch.threadId() != null;
                if (isThread != null && isThread != channelIsThread) {
                    return false;
                }
            }

            if (filter.isEewAlertPresent() && ch.eewAlert() != filter.getEewAlert()) {
                return false;
            }

            if (filter.isEewPredictionPresent() && ch.eewPrediction() != filter.getEewPrediction()) {
                return false;
            }

            if (filter.isEewDecimationPresent() && ch.eewDecimation() != filter.getEewDecimation()) {
                return false;
            }

            if (filter.isQuakeInfoPresent() && ch.quakeInfo() != filter.getQuakeInfo()) {
                return false;
            }

            if (filter.isIntensityPresent()) {
                SeismicIntensity minIntensity = ch.minIntensity();
                if (minIntensity == null || minIntensity.ordinal() > filter.getIntensity().ordinal()) {
                    return false;
                }
            }

            if (filter.isWebhookIdPresent()) {
                if (ch.webhook() == null || ch.webhook().getId() != filter.getWebhookId()) {
                    return false;
                }
            }

            return true;
        };
    }
}
