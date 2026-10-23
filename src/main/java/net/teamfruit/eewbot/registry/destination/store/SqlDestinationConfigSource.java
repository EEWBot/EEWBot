package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;

/**
 * SQL configuration reader. Revision and destinations use one transaction.
 * SQLite provides a stable read snapshot. PostgreSQL currently inherits the
 * connection's isolation level: READ COMMITTED can observe a concurrent commit
 * between the two reads. Newer destinations with an earlier revision are allowed
 * by DestinationConfigSource; background revision checks catch up after writes stop.
 */
public class SqlDestinationConfigSource implements DestinationConfigSource {

    private final ChannelRegistrySql sqlRegistry;
    private final ConfigRevisionStore revisionStore;

    public SqlDestinationConfigSource(ChannelRegistrySql sqlRegistry, ConfigRevisionStore revisionStore) {
        this.sqlRegistry = sqlRegistry;
        this.revisionStore = revisionStore;
    }

    @Override
    public long getRevision() {
        return this.revisionStore.getRevision();
    }

    @Override
    public DeliverySnapshot loadSnapshot() {
        return this.sqlRegistry.getDsl().transactionResult(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            long revision = this.revisionStore.getRevisionWithDsl(tx);
            return new DeliverySnapshot(revision, this.sqlRegistry.loadAllForSnapshotWithDsl(tx));
        });
    }
}
