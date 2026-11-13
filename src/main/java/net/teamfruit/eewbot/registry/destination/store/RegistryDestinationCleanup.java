package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationCleanup;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/** Local cleanup adapter preserving registry write and persistence semantics. */
public final class RegistryDestinationCleanup implements DestinationCleanup {

    private final DestinationAdminRegistry registry;

    public RegistryDestinationCleanup(DestinationAdminRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void guildDeleted(long guildId) throws IOException {
        this.registry.removeByGuildId(guildId);
        this.registry.save();
    }

    @Override
    public void destinationDeleted(long targetId) throws IOException {
        this.registry.remove(targetId);
        this.registry.save();
    }

    @Override
    public void destinationsUnavailable(Collection<Long> targetIds) throws IOException {
        targetIds.forEach(this.registry::remove);
        this.registry.save();
    }

    @Override
    public void webhooksUnavailable(Map<Long, DeliveryTarget> failedTargets) throws IOException {
        // The local registry owns the configuration, so the target ID alone identifies the webhook to clear.
        failedTargets.keySet().forEach(targetId -> this.registry.setWebhook(targetId, null));
        this.registry.save();
    }

    @Override
    public int webhookUrlsUnavailable(Collection<String> webhookUrls) {
        return this.registry.clearWebhookByUrls(webhookUrls);
    }

    @Override
    public void save() throws IOException {
        this.registry.save();
    }
}
