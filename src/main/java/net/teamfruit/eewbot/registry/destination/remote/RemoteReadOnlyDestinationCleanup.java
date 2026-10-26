package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.destination.DestinationCleanup;

import java.util.Collection;

/** Staging adapter until an independently authorized management API exists. */
public final class RemoteReadOnlyDestinationCleanup implements DestinationCleanup {
    private void unapplied(String kind, int count) {
        if (count > 0) Log.logger.warn("Remote cleanup NOT applied: kind={}, count={}; D1 management API is not implemented", kind, count);
    }

    @Override public void guildDeleted(long guildId) { unapplied("guild", 1); }
    @Override public void destinationDeleted(long targetId) { unapplied("destination", 1); }
    @Override public void destinationsUnavailable(Collection<Long> targetIds) { unapplied("destination", targetIds.size()); }
    @Override public void webhooksUnavailable(Collection<Long> targetIds) { unapplied("webhook", targetIds.size()); }
    @Override public int webhookUrlsUnavailable(Collection<String> webhookUrls) {
        unapplied("webhook-url", webhookUrls.size());
        return 0;
    }
}
