package net.teamfruit.eewbot.registry.destination.store;

import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigRevisionStoreTest {

    @TempDir
    Path tempDir;

    private ChannelRegistrySql sqlRegistry;
    private ConfigRevisionStore revisionStore;

    @BeforeEach
    void setUp() {
        Path dbPath = this.tempDir.resolve("test.db");
        this.sqlRegistry = ChannelRegistrySql.forSQLite(dbPath);
        DatabaseInitializer.migrate(this.sqlRegistry.getDataSource(), SQLDialect.SQLITE);
        this.revisionStore = new ConfigRevisionStore(this.sqlRegistry.getDsl(), SQLDialect.SQLITE);
    }

    @AfterEach
    void tearDown() {
        if (this.sqlRegistry != null) {
            this.sqlRegistry.close();
        }
    }

    @Test
    @DisplayName("initial revision should be 0")
    void initialRevisionIsZero() {
        assertThat(this.revisionStore.getRevision()).isEqualTo(0L);
    }

    @Test
    @DisplayName("incrementRevision should return 1 on first call")
    void firstIncrementReturnsOne() {
        long result = this.revisionStore.incrementRevision();
        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("incrementRevision should increment monotonically")
    void incrementIsMonotonic() {
        long r1 = this.revisionStore.incrementRevision();
        long r2 = this.revisionStore.incrementRevision();
        long r3 = this.revisionStore.incrementRevision();

        assertThat(r1).isEqualTo(1L);
        assertThat(r2).isEqualTo(2L);
        assertThat(r3).isEqualTo(3L);
    }

    @Test
    @DisplayName("getRevision should reflect latest increment")
    void getRevisionReflectsIncrement() {
        this.revisionStore.incrementRevision();
        this.revisionStore.incrementRevision();

        assertThat(this.revisionStore.getRevision()).isEqualTo(2L);
    }

    @Test
    @DisplayName("incrementWithDsl should work with transactional DSLContext")
    void incrementWithDslInTransaction() {
        this.sqlRegistry.getDsl().transaction(ctx -> {
            long rev = this.revisionStore.incrementWithDsl(ctx.dsl());
            assertThat(rev).isEqualTo(1L);

            rev = this.revisionStore.incrementWithDsl(ctx.dsl());
            assertThat(rev).isEqualTo(2L);
        });

        assertThat(this.revisionStore.getRevision()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getRevisionWithDsl should work with transactional DSLContext")
    void getRevisionWithDslInTransaction() {
        this.revisionStore.incrementRevision();

        this.sqlRegistry.getDsl().transaction(ctx -> {
            long rev = this.revisionStore.getRevisionWithDsl(ctx.dsl());
            assertThat(rev).isEqualTo(1L);
        });
    }
}
