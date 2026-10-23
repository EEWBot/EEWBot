package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PostgresqlDestinationStorageTest extends SqlDestinationStorageContract {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Override
    ChannelRegistrySql openRegistry() {
        ConfigV2.PostgreSQL config = new ConfigV2.PostgreSQL();
        config.setHost(POSTGRES.getHost());
        config.setPort(POSTGRES.getFirstMappedPort());
        config.setDatabase(POSTGRES.getDatabaseName());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        return ChannelRegistrySql.forPostgreSQL(config);
    }

    @Test
    void readCommittedReproducesExistingSnapshotConsistencyLimitation() {
        this.admin.put(1, Channel.createDefault(null, 1L, null, "ja_jp"));
        ConfigRevisionStore interleaved = interleavedRevision(false);

        var snapshot = new SqlDestinationConfigSource(this.sql, interleaved).loadSnapshot();

        // Characterizes the existing bug; production isolation is unchanged.
        assertThat(snapshot.getRevision()).isEqualTo(1);
        assertThat(snapshot.size()).isEqualTo(2);
        assertThat(this.source.getRevision()).isEqualTo(2);
    }

    @Test
    void transactionLocalRepeatableReadWouldKeepSnapshotConsistent() {
        this.admin.put(1, Channel.createDefault(null, 1L, null, "ja_jp"));

        var snapshot = new SqlDestinationConfigSource(this.sql, interleavedRevision(true)).loadSnapshot();

        assertThat(snapshot.getRevision()).isEqualTo(1);
        assertThat(snapshot.size()).isEqualTo(1);
        assertThat(this.source.getRevision()).isEqualTo(2);
        assertThat(this.sql.getDsl().fetch("SHOW transaction_isolation").get(0).get(0, String.class)).isEqualTo("read committed");
    }

    private ConfigRevisionStore interleavedRevision(boolean repeatableRead) {
        return new ConfigRevisionStore(this.sql.getDsl(), this.sql.getDialect()) {
            @Override
            public long getRevisionWithDsl(DSLContext tx) {
                if (repeatableRead) tx.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ");
                long revision = super.getRevisionWithDsl(tx);
                admin.put(2, Channel.createDefault(null, 2L, null, "en_us"));
                return revision;
            }
        };
    }
}
