package net.teamfruit.eewbot.registry.destination;

import net.teamfruit.eewbot.registry.destination.delivery.DeliveryPartition;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;

public interface DestinationDeliveryRegistry {

    DeliveryPartition getDeliveryChannels(ChannelFilter filter);

}
