package net.teamfruit.eewbot.registry.destination.store;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;

import static org.jooq.impl.DSL.*;

/**
 * Manages the channels_revision counter in config_meta table.
 * Used for cache invalidation when channels are modified.
 */
public class ConfigRevisionStore {

    private static final Table<?> CONFIG_META = table(name("config_meta"));
    private static final Field<Integer> ID = field(name("id"), Integer.class);
    private static final Field<Long> CHANNELS_REVISION = field(name("channels_revision"), Long.class);

    private final DSLContext dsl;
    private final SQLDialect dialect;

    public ConfigRevisionStore(DSLContext dsl, SQLDialect dialect) {
        this.dsl = dsl;
        this.dialect = dialect;
    }

    /**
     * Get the current channels revision.
     */
    public long getRevision() {
        return getRevisionWithDsl(this.dsl);
    }

    /**
     * Get the current channels revision using the provided DSLContext (for transactional use).
     */
    public long getRevisionWithDsl(DSLContext tx) {
        Long revision = tx.select(CHANNELS_REVISION)
                .from(CONFIG_META)
                .where(ID.eq(1))
                .fetchOne(CHANNELS_REVISION);
        return revision != null ? revision : 0L;
    }

    /**
     * Increment the channels revision and return the new value.
     */
    public long incrementRevision() {
        return incrementWithDsl(this.dsl);
    }

    /**
     * Increment the channels revision using the provided DSLContext (for transactional use).
     * PostgreSQL uses RETURNING, SQLite uses UPDATE + SELECT in same transaction.
     */
    public long incrementWithDsl(DSLContext tx) {
        if (this.dialect == SQLDialect.POSTGRES) {
            return tx.update(CONFIG_META)
                    .set(CHANNELS_REVISION, CHANNELS_REVISION.plus(1))
                    .where(ID.eq(1))
                    .returning(CHANNELS_REVISION)
                    .fetchOne(CHANNELS_REVISION);
        } else {
            // SQLite: UPDATE + SELECT in same transaction
            tx.update(CONFIG_META)
                    .set(CHANNELS_REVISION, CHANNELS_REVISION.plus(1))
                    .where(ID.eq(1))
                    .execute();
            return getRevisionWithDsl(tx);
        }
    }
}
