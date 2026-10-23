package net.teamfruit.eewbot.registry.destination;

import java.io.IOException;

/**
 * Gateway-originated cleanup, separate from interactive management operations.
 * A future deployment can forward these events to the configuration owner.
 */
public interface DestinationCleanup {

    void guildDeleted(long guildId) throws IOException;

    /** Delete a channel or thread by destination target ID. */
    void destinationDeleted(long targetId) throws IOException;

    /** Preserve local registry deletion and persistence semantics. */
    static DestinationCleanup using(DestinationAdminRegistry registry) {
        return new DestinationCleanup() {
            @Override
            public void guildDeleted(long guildId) throws IOException {
                registry.removeByGuildId(guildId);
                registry.save();
            }

            @Override
            public void destinationDeleted(long targetId) throws IOException {
                registry.remove(targetId);
                registry.save();
            }
        };
    }
}
