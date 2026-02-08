package net.teamfruit.eewbot.registry.destination.legacy;

import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationDeliveryRegistry;

/**
 * Legacy unified registry interface.
 * New code should use {@link DestinationDeliveryRegistry} or {@link DestinationAdminRegistry} instead.
 *
 * @deprecated Use {@link DestinationDeliveryRegistry} for delivery and {@link DestinationAdminRegistry} for admin.
 */
@Deprecated
public interface ChannelRegistry extends DestinationDeliveryRegistry, DestinationAdminRegistry {

}
