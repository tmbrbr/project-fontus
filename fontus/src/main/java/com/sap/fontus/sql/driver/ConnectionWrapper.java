package com.sap.fontus.sql.driver;

import com.sap.fontus.sql.tainter.QueryCache;
import com.sap.fontus.sql.tainter.QueryParameters;
import com.sap.fontus.utils.Pair;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;


@SuppressWarnings("JDBCPrepareStatementWithNonConstantString")
public class ConnectionWrapper extends AbstractWrapper implements Connection {

    private final Connection delegate;

    public static ConnectionWrapper wrap(Connection delegate) {
        if (delegate == null) {
            return null;
        }
        return new ConnectionWrapper(delegate);
    }

    protected ConnectionWrapper(Connection delegate) {
        super(delegate);
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate must not be null");
        }
        this.delegate = delegate;
    }

    public Connection getDelegate() {
        return this.delegate;
    }

    @Override
    public Statement createStatement() throws SQLException {
        return StatementWrapper.wrap(this.delegate.createStatement());
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return StatementWrapper.wrap(this.delegate.createStatement(resultSetType, resultSetConcurrency));
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return StatementWrapper.wrap(this.delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return PreparedStatementWrapper.wrap(this.delegate.prepareStatement(tainted.x),sql,tainted.x, tainted.y);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return PreparedStatementWrapper.wrap(this.delegate.prepareStatement(tainted.x, resultSetType, resultSetConcurrency),sql,tainted.x, tainted.y);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return PreparedStatementWrapper.wrap(this.delegate.prepareStatement(tainted.x, resultSetType, resultSetConcurrency, resultSetHoldability),sql,tainted.x, tainted.y);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return PreparedStatementWrapper.wrap(this.delegate.prepareStatement(tainted.x, autoGeneratedKeys),sql,tainted.x, tainted.y);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        //TODO: fix indexes ?
        return PreparedStatementWrapper.wrap(this.delegate.prepareStatement(tainted.x, columnIndexes),sql,tainted.x, tainted.y);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return PreparedStatementWrapper.wrap(this.delegate.prepareStatement(tainted.x, columnNames),sql,tainted.x, tainted.y);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return CallableStatementWrapper.wrap(this.delegate.prepareCall(tainted.x, resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return CallableStatementWrapper.wrap(this.delegate.prepareCall(tainted.x));
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        Pair<String, QueryParameters> tainted = QueryCache.INSTANCE.parseQuery(sql);
        return CallableStatementWrapper.wrap(this.delegate.prepareCall(tainted.x, resultSetType, resultSetConcurrency));
    }

    @Override
    public void commit() throws SQLException {
        this.delegate.commit();
    }

    @Override
    public void rollback() throws SQLException {
        this.delegate.rollback();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        this.delegate.rollback(savepoint);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return this.delegate.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.delegate.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return this.delegate.getAutoCommit();
    }

    @Override
    public void close() throws SQLException {
        this.delegate.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.delegate.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return this.delegate.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.delegate.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return this.delegate.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        this.delegate.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return this.delegate.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        this.delegate.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return this.delegate.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return this.delegate.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        this.delegate.clearWarnings();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return this.delegate.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.delegate.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        this.delegate.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return this.delegate.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return this.delegate.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return this.delegate.setSavepoint(name);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.delegate.releaseSavepoint(savepoint);
    }

    @Override
    public Clob createClob() throws SQLException {
        return this.delegate.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return this.delegate.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return this.delegate.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return this.delegate.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return this.delegate.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        this.delegate.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        this.delegate.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return this.delegate.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return this.delegate.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return this.delegate.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return this.delegate.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        this.delegate.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return this.delegate.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        this.delegate.abort(executor);
    }

    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.delegate.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return this.delegate.getNetworkTimeout();
    }
}
