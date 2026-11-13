package net.teamfruit.eewbot.registry.destination.delivery;

import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** An in-memory source with independently controllable reads and failures. */
final class FakeDestinationConfigSource implements DestinationConfigSource {

    private final Supplier<DeliverySnapshot> snapshots;
    private final LongSupplier revisions;

    FakeDestinationConfigSource(Supplier<DeliverySnapshot> snapshots, LongSupplier revisions) {
        this.snapshots = snapshots;
        this.revisions = revisions;
    }

    @Override
    public long getRevision() {
        return this.revisions.getAsLong();
    }

    @Override
    public DeliverySnapshot loadSnapshot() {
        return this.snapshots.get();
    }
}
