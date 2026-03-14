package net.teamfruit.eewbot.registry.destination.store;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Blocks datasource shutdown until all in-flight connections are closed.
 */
public class ShutdownAwareDataSource implements DataSource {

    private final DataSource delegate;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public ShutdownAwareDataSource(final DataSource delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public void shutdown() {
        if (!this.shuttingDown.compareAndSet(false, true)) {
            return;
        }

        this.lock.writeLock().lock();
        try {
            closeDelegate();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return withReadLock(this.delegate::getConnection);
    }

    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return withReadLock(() -> this.delegate.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return this.delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(final PrintWriter out) throws SQLException {
        this.delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        this.delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return this.delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return this.delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return this.delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isInstance(this) || this.delegate.isWrapperFor(iface);
    }

    private Connection withReadLock(final ConnectionSupplier supplier) throws SQLException {
        final Lock readLock = this.lock.readLock();
        readLock.lock();
        if (this.shuttingDown.get()) {
            readLock.unlock();
            throw new SQLException("DataSource is shutting down");
        }

        final Connection connection;
        try {
            connection = supplier.get();
        } catch (final SQLException | RuntimeException e) {
            readLock.unlock();
            throw e;
        }

        try {
            return wrapConnection(connection, readLock);
        } catch (final RuntimeException e) {
            try {
                connection.close();
            } catch (final SQLException suppressed) {
                e.addSuppressed(suppressed);
            }
            readLock.unlock();
            throw e;
        }
    }

    private Connection wrapConnection(final Connection realConnection, final Lock readLock) {
        final AtomicBoolean released = new AtomicBoolean(false);
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                try {
                    return invokeDelegate(method, realConnection, args);
                } finally {
                    if (released.compareAndSet(false, true)) {
                        readLock.unlock();
                    }
                }
            }

            if ("unwrap".equals(method.getName()) && method.getParameterCount() == 1) {
                final Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    return proxy;
                }
            }
            if ("isWrapperFor".equals(method.getName()) && method.getParameterCount() == 1) {
                final Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    return true;
                }
            }

            return invokeDelegate(method, realConnection, args);
        };

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                handler
        );
    }

    private Object invokeDelegate(final Method method, final Connection realConnection, final Object[] args) throws Throwable {
        try {
            return method.invoke(realConnection, args);
        } catch (final InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void closeDelegate() {
        if (this.delegate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (final Exception e) {
                throw new IllegalStateException("Failed to close datasource", e);
            }
        }
    }

    @FunctionalInterface
    private interface ConnectionSupplier {
        Connection get() throws SQLException;
    }
}
