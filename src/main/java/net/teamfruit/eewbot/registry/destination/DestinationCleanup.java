package net.teamfruit.eewbot.registry.destination;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;

/**
 * Cleanup observed by the Gateway and delivery paths, separate from interactive management.
 * A future deployment can forward these events to the configuration owner.
 */
public interface DestinationCleanup {

    void guildDeleted(long guildId) throws IOException;

    /** Delete a channel or thread by destination target ID. */
    void destinationDeleted(long targetId) throws IOException;

    void destinationsUnavailable(Collection<Long> targetIds) throws IOException;

    void webhooksUnavailable(Collection<Long> targetIds) throws IOException;

    /** Uses the immutable targets actually sent, so remote cleanup can compare webhook IDs. */
    default void webhooksUnavailable(Map<Long, DeliveryTarget> failedTargets) throws IOException {
        this.webhooksUnavailable(failedTargets.keySet());
    }

    int webhookUrlsUnavailable(Collection<String> webhookUrls);

    /** Flush pending cleanup at shutdown; transactional implementations need no extra action. */
    default void save() throws IOException {
    }

}
