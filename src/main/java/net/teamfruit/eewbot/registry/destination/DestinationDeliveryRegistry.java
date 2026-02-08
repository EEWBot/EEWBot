package net.teamfruit.eewbot.registry.destination;

import net.teamfruit.eewbot.registry.destination.model.ChannelBase;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;

import java.util.Map;

public interface DestinationDeliveryRegistry {

    Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter);

    boolean isWebhookForThread(long webhookId, long targetId);

}
