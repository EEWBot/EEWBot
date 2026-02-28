package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;

import java.util.*;

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
         * Convert to DeliveryTarget for delivery.
         */
        public DeliveryTarget toDeliveryTarget(long targetId) {
            return new DeliveryTarget(
                    targetId,
                    this.lang,
                    this.webhook != null ? this.webhook.getUrl() : null
            );
        }
    }

    private final long revision;
    private final Map<Long, DeliveryChannel> byTargetId;

    // Pre-computed indexes for efficient lookup
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
     */
    public DeliveryPartition getPartitionedByWebhook(ChannelFilter filter) {
        Map<Long, DeliveryTarget> withWebhook = new HashMap<>();
        Map<Long, DeliveryTarget> withoutWebhook = new HashMap<>();

        for (long targetId : this.targetsWithWebhook) {
            DeliveryChannel channel = this.byTargetId.get(targetId);
            if (filter == null || filter.test(channel)) {
                withWebhook.put(targetId, channel.toDeliveryTarget(targetId));
            }
        }

        for (long targetId : this.targetsWithoutWebhook) {
            DeliveryChannel channel = this.byTargetId.get(targetId);
            if (filter == null || filter.test(channel)) {
                withoutWebhook.put(targetId, channel.toDeliveryTarget(targetId));
            }
        }

        return new DeliveryPartition(
                Collections.unmodifiableMap(withWebhook),
                Collections.unmodifiableMap(withoutWebhook)
        );
    }
}
