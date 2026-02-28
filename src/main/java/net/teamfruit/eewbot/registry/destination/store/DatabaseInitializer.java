package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.Log;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.jooq.SQLDialect;

import javax.sql.DataSource;

public class DatabaseInitializer {

    public static void migrate(DataSource dataSource, SQLDialect dialect) {
        String location = switch (dialect) {
            case SQLITE -> "classpath:db/migration/sqlite";
            case POSTGRES -> "classpath:db/migration/postgres";
            default -> throw new IllegalArgumentException("Unsupported SQL dialect: " + dialect);
        };

        Log.logger.info("Running Flyway migrations from: {}", location);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .callbacks(new Callback[0])
                .load();

        flyway.migrate();

        Log.logger.info("Database migration completed successfully");
    }
}
