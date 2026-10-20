package net.teamfruit.eewbot.registry.destination;

import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface DestinationDeliveryRegistry {

    DeliveryPartition getDeliveryChannels(ChannelFilter filter);

    default DeliveryPartition getDeliveryChannelsMatchingAny(List<ChannelFilter> filters) {
        Map<Long, DeliveryTarget> webhook = new HashMap<>();
        Map<Long, DeliveryTarget> direct = new HashMap<>();
        for (ChannelFilter filter : filters) {
            DeliveryPartition partition = getDeliveryChannels(filter);
            webhook.putAll(partition.webhook());
            direct.putAll(partition.direct());
        }
        // A configuration reload between lookups must not send twice via different transports.
        direct.keySet().removeAll(webhook.keySet());
        return new DeliveryPartition(Map.copyOf(webhook), Map.copyOf(direct));
    }

}
