package net.teamfruit.eewbot.registry.destination.store;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ShutdownAwareDataSourceTest {

    @Test
    void getConnection_succeedsBeforeShutdown() throws Exception {
        TestDataSource delegate = new TestDataSource();
        ShutdownAwareDataSource dataSource = new ShutdownAwareDataSource(delegate);

        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }

        assertFalse(delegate.isClosed());
        assertEquals(0, delegate.getCloseCount());
    }

    @Test
    void getConnection_throwsAfterShutdown() {
        TestDataSource delegate = new TestDataSource();
        ShutdownAwareDataSource dataSource = new ShutdownAwareDataSource(delegate);

        dataSource.shutdown();

        SQLException exception = assertThrows(SQLException.class, dataSource::getConnection);
        assertEquals("DataSource is shutting down", exception.getMessage());
        assertTrue(delegate.isClosed());
        assertEquals(1, delegate.getCloseCount());
    }

    @Test
    void shutdown_blocksUntilActiveConnectionIsClosed() throws Exception {
        TestDataSource delegate = new TestDataSource();
        ShutdownAwareDataSource dataSource = new ShutdownAwareDataSource(delegate);
        Connection connection = dataSource.getConnection();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> shutdownFuture = executor.submit(dataSource::shutdown);

            assertThrows(TimeoutException.class, () -> shutdownFuture.get(200, TimeUnit.MILLISECONDS));
            assertFalse(delegate.isClosed());

            connection.close();

            shutdownFuture.get(1, TimeUnit.SECONDS);
            assertTrue(delegate.isClosed());
            assertEquals(1, delegate.getCloseCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void connectionClose_releasesReadLockOnlyOnce() throws Exception {
        TestDataSource delegate = new TestDataSource();
        ShutdownAwareDataSource dataSource = new ShutdownAwareDataSource(delegate);
        Connection connection = dataSource.getConnection();

        assertDoesNotThrow(connection::close);
        assertDoesNotThrow(connection::close);
        assertDoesNotThrow(dataSource::shutdown);

        assertTrue(delegate.isClosed());
        assertEquals(1, delegate.getCloseCount());
    }

    private static final class TestDataSource implements DataSource, AutoCloseable {

        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicInteger closeCount = new AtomicInteger(0);

        @Override
        public Connection getConnection() throws SQLException {
            if (this.closed.get()) {
                throw new SQLException("delegate closed");
            }
            return createConnection();
        }

        @Override
        public Connection getConnection(final String username, final String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(final int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public void close() {
            this.closed.set(true);
            this.closeCount.incrementAndGet();
        }

        boolean isClosed() {
            return this.closed.get();
        }

        int getCloseCount() {
            return this.closeCount.get();
        }

        private Connection createConnection() {
            AtomicBoolean connectionClosed = new AtomicBoolean(false);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "close" -> {
                            connectionClosed.set(true);
                            yield null;
                        }
                        case "isClosed" -> connectionClosed.get();
                        case "unwrap" -> {
                            Class<?> iface = (Class<?>) args[0];
                            if (iface.isInstance(proxy)) {
                                yield proxy;
                            }
                            throw new SQLException("Not a wrapper for " + iface.getName());
                        }
                        case "isWrapperFor" -> {
                            Class<?> iface = (Class<?>) args[0];
                            yield iface.isInstance(proxy);
                        }
                        case "toString" -> "TestConnection";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(final Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            return null;
        }
    }
}
