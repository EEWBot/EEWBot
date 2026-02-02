package net.teamfruit.eewbot.registry.channel;

import java.util.List;

/**
 * Loads DeliverySnapshot from database.
 * Ensures revision and channels are read in the same transaction for consistency.
 */
public class DeliverySnapshotLoader {

    private final ChannelRegistrySql sqlRegistry;
    private final ConfigRevisionStore revisionStore;

    public DeliverySnapshotLoader(ChannelRegistrySql sqlRegistry, ConfigRevisionStore revisionStore) {
        this.sqlRegistry = sqlRegistry;
        this.revisionStore = revisionStore;
    }

    /**
     * Load a consistent snapshot by reading revision and channels in the same transaction.
     */
    public DeliverySnapshot loadInTransaction() {
        return this.sqlRegistry.getDsl().transactionResult(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            long revision = this.revisionStore.getRevisionWithDsl(tx);
            List<DeliverySnapshot.DeliveryChannel> channels = this.sqlRegistry.loadAllForSnapshotWithDsl(tx);
            return new DeliverySnapshot(revision, channels);
        });
    }
}
