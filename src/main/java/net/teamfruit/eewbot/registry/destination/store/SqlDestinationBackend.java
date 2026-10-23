package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.DestinationBackend;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns SQL connection setup, migrations, registries and datasource shutdown. */
public final class SqlDestinationBackend implements DestinationBackend {

    private final ChannelRegistrySql sql;
    private final ConfigRevisionStore revisionStore;
    private final DestinationConfigSource configSource;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private SqlDestinationBackend(ChannelRegistrySql sql) {
        this.sql = sql;
        this.revisionStore = new ConfigRevisionStore(sql.getDsl(), sql.getDialect());
        this.configSource = new SqlDestinationConfigSource(sql, this.revisionStore);
    }

    public static SqlDestinationBackend open(ConfigV2.Database config, Path dataDirectory) throws IOException {
        String dbType = config.getType();
        ChannelRegistrySql sql = switch (dbType.toLowerCase()) {
            case "postgresql" -> ChannelRegistrySql.forPostgreSQL(config.getPostgresql());
            case "sqlite" -> {
                Path path = dataDirectory != null
                        ? Paths.get(dataDirectory.toString(), config.getSqlite().getPath())
                        : Paths.get(config.getSqlite().getPath());
                yield ChannelRegistrySql.forSQLite(path);
            }
            case "json", "redis" -> throw new IllegalStateException(
                    "database.type '" + dbType + "' is no longer supported. Migrate to 'sqlite' or "
                            + "'postgresql' with the ChannelMigration tool of EEWBot 2.9.x before upgrading.");
            default -> throw new IllegalStateException(
                    "Unknown database.type: '" + dbType + "'. Supported values: sqlite, postgresql");
        };
        return initialize(sql);
    }

    // Takes ownership immediately, including when migration fails.
    static SqlDestinationBackend initialize(ChannelRegistrySql sql) {
        try {
            DatabaseInitializer.migrate(sql.getDataSource(), sql.getDialect());
            return new SqlDestinationBackend(sql);
        } catch (RuntimeException | Error e) {
            try {
                sql.close();
            } catch (RuntimeException | Error closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    @Override
    public DestinationConfigSource getConfigSource() {
        return this.configSource;
    }

    @Override
    public DestinationAdminRegistry createAdminRegistry(Runnable onWrite) {
        return new SqlAdminRegistry(this.sql, this.revisionStore, onWrite);
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.sql.close();
        }
    }
}
