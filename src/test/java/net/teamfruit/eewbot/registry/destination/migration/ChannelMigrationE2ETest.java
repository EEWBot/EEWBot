package net.teamfruit.eewbot.registry.destination.migration;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.ConfigRevisionStore;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.*;

@Tag("integration")
class ChannelMigrationE2ETest {

    @TempDir
    Path tempDir;

    private static final Table<?> DESTINATIONS = table(name("destinations"));
    private static final Table<?> DATA_MIGRATIONS = table(name("data_migrations"));
    private static final Field<Long> TARGET_ID = field(name("target_id"), Long.class);
    private static final Field<String> NAME_FIELD = field(name("name"), String.class);

    /**
     * Builds a SQLite source database containing two channels.
     */
    private Path createSourceDb() throws IOException {
        Path dbPath = this.tempDir.resolve("source.db");
        ChannelRegistrySql source = ChannelRegistrySql.forSQLite(dbPath);
        try {
            DatabaseInitializer.migrate(source.getDataSource(), SQLDialect.SQLITE);
            source.put(1001L, new Channel(100L, 1001L, null,
                    true, false, false, true, false, SeismicIntensity.ONE, null, "ja_jp"));
            source.put(1002L, new Channel(100L, 1002L, null,
                    false, true, false, false, false, SeismicIntensity.THREE, null, "en_us"));
        } finally {
            source.close();
        }
        return dbPath;
    }

    private String[] migrationArgs(Path source, String destPath, String... extra) {
        String[] base = {"--source", "sqlite", "--source-path", source.toString(),
                "--dest", "sqlite", "--dest-path", destPath};
        if (extra.length == 0) {
            return base;
        }
        String[] combined = new String[base.length + extra.length];
        System.arraycopy(base, 0, combined, 0, base.length);
        System.arraycopy(extra, 0, combined, base.length, extra.length);
        return combined;
    }

    private String destDbPath() {
        return this.tempDir.resolve("dest.db").toString();
    }

    @Nested
    @DisplayName("Migration tracking")
    class MigrationTrackingTests {

        @Test
        @DisplayName("Idempotent: second run is a no-op with no duplicates")
        void idempotentMigration() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            String[] args = migrationArgs(source, destPath);

            int result1 = ChannelMigration.run(args);
            assertThat(result1).isZero();

            int result2 = ChannelMigration.run(args);
            assertThat(result2).isZero();

            // Verify no duplicates
            ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            try {
                Map<Long, Channel> all = registry.getAllChannels();
                assertThat(all).hasSize(2);
            } finally {
                registry.close();
            }
        }

        @Test
        @DisplayName("Migration is recorded in data_migrations table")
        void migrationRecorded() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            String[] args = migrationArgs(source, destPath);

            ChannelMigration.run(args);

            ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            try {
                boolean exists = registry.getDsl().fetchExists(
                        registry.getDsl().selectFrom(DATA_MIGRATIONS)
                                .where(NAME_FIELD.eq("sqlite_to_sql_channels_v1"))
                );
                assertThat(exists).isTrue();
            } finally {
                registry.close();
            }
        }

        @Test
        @DisplayName("Migration increments channels_revision")
        void migrationIncrementsRevision() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            String[] args = migrationArgs(source, destPath);

            ChannelMigration.run(args);

            ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            try {
                long revision = new ConfigRevisionStore(registry.getDsl(), SQLDialect.SQLITE).getRevision();
                assertThat(revision).isGreaterThan(0);
            } finally {
                registry.close();
            }
        }

        @Test
        @DisplayName("isMigrationApplied returns true after success")
        void isMigrationAppliedAfterSuccess() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            String[] args = migrationArgs(source, destPath);

            ChannelMigration.run(args);

            ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            try {
                assertThat(ChannelMigration.isMigrationApplied(registry, "sqlite_to_sql_channels_v1")).isTrue();
            } finally {
                registry.close();
            }
        }
    }

    @Nested
    @DisplayName("Rollback on failure")
    class RollbackTests {

        @Test
        @DisplayName("Rollback on failure: trigger prevents insert, transaction rolls back")
        void rollbackOnFailure() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            // Create and migrate the dest DB schema first
            ChannelRegistrySql destRegistry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            DatabaseInitializer.migrate(destRegistry.getDataSource(), SQLDialect.SQLITE);

            // Add a trigger that fails after 1 row is inserted
            destRegistry.getDsl().execute("""
                    CREATE TRIGGER fail_after_first_insert
                    BEFORE INSERT ON destinations
                    WHEN (SELECT COUNT(*) FROM destinations) >= 1
                    BEGIN
                        SELECT RAISE(ABORT, 'trigger-forced failure for test');
                    END
                    """);
            destRegistry.close();

            // Now run migration — should fail due to trigger
            String[] args = migrationArgs(source, destPath);

            int result = ChannelMigration.run(args);
            assertThat(result).isEqualTo(1);

            // Verify rollback: no data in destinations or data_migrations
            ChannelRegistrySql verifyRegistry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            try {
                // Drop the trigger so we can query cleanly
                verifyRegistry.getDsl().execute("DROP TRIGGER IF EXISTS fail_after_first_insert");

                int destCount = verifyRegistry.getDsl()
                        .selectCount().from(DESTINATIONS)
                        .fetchOne(0, int.class);
                assertThat(destCount).isZero();

                int migrationCount = verifyRegistry.getDsl()
                        .selectCount().from(DATA_MIGRATIONS)
                        .fetchOne(0, int.class);
                assertThat(migrationCount).isZero();
            } finally {
                verifyRegistry.close();
            }
        }
    }

    @Nested
    @DisplayName("Dry run")
    class DryRunTests {

        @Test
        @DisplayName("Dry run writes no data")
        void dryRunNoWrites() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            String[] args = migrationArgs(source, destPath, "--dry-run");

            int result = ChannelMigration.run(args);
            assertThat(result).isZero();

            // Verify: schema exists but no data rows
            ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            try {
                int destCount = registry.getDsl()
                        .selectCount().from(DESTINATIONS)
                        .fetchOne(0, int.class);
                assertThat(destCount).isZero();

                int migrationCount = registry.getDsl()
                        .selectCount().from(DATA_MIGRATIONS)
                        .fetchOne(0, int.class);
                assertThat(migrationCount).isZero();
            } finally {
                registry.close();
            }
        }
    }

    @Nested
    @DisplayName("Run integration")
    class RunIntegrationTests {

        @Test
        @DisplayName("SQLite to SQLite migration succeeds")
        void sqliteToSqliteSuccess() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            String[] args = migrationArgs(source, destPath);

            int result = ChannelMigration.run(args);
            assertThat(result).isZero();

            ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(Path.of(destPath));
            try {
                Map<Long, Channel> all = registry.getAllChannels();
                assertThat(all).hasSize(2);
                assertThat(all).containsKey(1001L);
                assertThat(all).containsKey(1002L);

                Channel ch1 = all.get(1001L);
                assertThat(ch1.isEewAlert()).isTrue();
                assertThat(ch1.isQuakeInfo()).isTrue();
                assertThat(ch1.getLang()).isEqualTo("ja_jp");

                Channel ch2 = all.get(1002L);
                assertThat(ch2.isEewPrediction()).isTrue();
                assertThat(ch2.getMinIntensity()).isEqualTo(SeismicIntensity.THREE);
                assertThat(ch2.getLang()).isEqualTo("en_us");
            } finally {
                registry.close();
            }
        }

        @Test
        @DisplayName("Missing --source returns error code 1")
        void missingSourceReturnsError() {
            String destPath = destDbPath();
            int result = ChannelMigration.run(new String[]{"--dest", "sqlite", "--dest-path", destPath});
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("Dry run returns exit code 0")
        void dryRunReturnsZero() throws IOException {
            Path source = createSourceDb();
            String destPath = destDbPath();

            int result = ChannelMigration.run(migrationArgs(source, destPath, "--dry-run"));
            assertThat(result).isZero();
        }
    }
}
