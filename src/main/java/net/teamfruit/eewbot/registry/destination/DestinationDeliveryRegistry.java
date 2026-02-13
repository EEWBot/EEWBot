package net.teamfruit.eewbot.registry.destination;

import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;

public interface DestinationDeliveryRegistry {

    DeliveryPartition getChannelsPartitionedByWebhookPresent(ChannelFilter filter);

    boolean isWebhookForThread(long webhookId, long targetId);

}
