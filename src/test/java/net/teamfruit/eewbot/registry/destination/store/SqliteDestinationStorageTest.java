package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.destination.model.Channel;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class SqliteDestinationStorageTest extends SqlDestinationStorageContract {

    @TempDir
    Path directory;

    @Override
    ChannelRegistrySql openRegistry() throws IOException {
        return ChannelRegistrySql.forSQLite(this.directory.resolve("destinations.db"));
    }

    @Test
    void snapshotKeepsRevisionAndDestinationsConsistentAcrossConcurrentCommit() {
        this.admin.put(1, Channel.createDefault(null, 1L, null, "ja_jp"));
        ConfigRevisionStore interleaved = new ConfigRevisionStore(this.sql.getDsl(), this.sql.getDialect()) {
            @Override
            public long getRevisionWithDsl(DSLContext tx) {
                long revision = super.getRevisionWithDsl(tx);
                admin.put(2, Channel.createDefault(null, 2L, null, "en_us"));
                return revision;
            }
        };

        var snapshot = new SqlDestinationConfigSource(this.sql, interleaved).loadSnapshot();

        assertThat(snapshot.getRevision()).isEqualTo(1);
        assertThat(snapshot.size()).isEqualTo(1);
        assertThat(this.source.getRevision()).isEqualTo(2);
    }
}
