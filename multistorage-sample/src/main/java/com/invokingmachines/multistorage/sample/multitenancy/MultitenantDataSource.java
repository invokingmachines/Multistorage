package com.invokingmachines.multistorage.sample.multitenancy;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.util.function.Supplier;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;

public class MultitenantDataSource implements DataSource {

    private final DataSource delegate;
    private final Supplier<TenantSchemaRegistry> tenantSchemaRegistry;

    public MultitenantDataSource(DataSource delegate, Supplier<TenantSchemaRegistry> tenantSchemaRegistry) {
        this.delegate = delegate;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
    }

    public DataSource getDelegate() {
        return delegate;
    }

    public static DataSource unwrapDelegate(DataSource dataSource) {
        DataSource current = dataSource;
        while (current instanceof MultitenantDataSource m) {
            current = m.getDelegate();
        }
        return current;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection raw = delegate.getConnection();
        return configure(raw);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection raw = delegate.getConnection(username, password);
        return configure(raw);
    }

    private Connection configure(Connection raw) throws SQLException {
        String code = TenantContext.getTenantCode();
        String schema = code == null ? null : tenantSchemaRegistry.get().getSchemaForCode(code).orElse(null);
        applySearchPath(raw, schema);
        return wrapConnection(raw);
    }

    private static void applySearchPath(Connection c, String schema) throws SQLException {
        try (Statement st = c.createStatement()) {
            if (schema == null || schema.isBlank()) {
                st.execute("SET search_path TO public");
            } else {
                st.execute("SET search_path TO \"" + escapeIdentifier(schema) + "\", public");
            }
        }
    }

    private static String escapeIdentifier(String schema) {
        return schema.replace("\"", "\"\"");
    }

    private static Connection wrapConnection(Connection raw) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                resetSearchPathQuietly(raw);
            }
            return method.invoke(raw, args);
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private static void resetSearchPathQuietly(Connection c) {
        try {
            if (c == null || c.isClosed()) {
                return;
            }
            try (Statement st = c.createStatement()) {
                st.execute("SET search_path TO public");
            }
        } catch (SQLException ignored) {
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
