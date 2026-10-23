package net.teamfruit.eewbot.registry.destination.store;

import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlDestinationBackendTest {

    @Test
    void migrationFailureClosesAcquiredRegistry() {
        ChannelRegistrySql sql = mock(ChannelRegistrySql.class);
        DataSource dataSource = mock(DataSource.class);
        when(sql.getDataSource()).thenReturn(dataSource);
        when(sql.getDialect()).thenReturn(SQLDialect.SQLITE);
        IllegalStateException failure = new IllegalStateException("migration failed");
        try (var migration = mockStatic(DatabaseInitializer.class)) {
            migration.when(() -> DatabaseInitializer.migrate(dataSource, SQLDialect.SQLITE)).thenThrow(failure);

            assertThatThrownBy(() -> SqlDestinationBackend.initialize(sql)).isSameAs(failure);
            verify(sql).close();
        }
    }

    @Test
    void closeIsIdempotent() {
        ChannelRegistrySql sql = mock(ChannelRegistrySql.class);
        when(sql.getDialect()).thenReturn(SQLDialect.SQLITE);
        try (var migration = mockStatic(DatabaseInitializer.class)) {
            SqlDestinationBackend backend = SqlDestinationBackend.initialize(sql);

            backend.close();
            backend.close();

            verify(sql).close();
        }
    }
}
