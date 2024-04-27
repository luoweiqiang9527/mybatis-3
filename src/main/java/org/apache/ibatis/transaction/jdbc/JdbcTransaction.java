/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.transaction.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionException;

/**
* {@link Transaction}，直接使用 JDBC 提交和回滚工具。它依赖于连接
 * 从 dataSource 中检索以管理事务范围。延迟连接检索，直到
 * getConnection（） 被调用。当自动提交处于打开状态时，忽略提交或回滚请求。
 *
 * @author Clinton Begin
 * @see JdbcTransactionFactory
 */
public class JdbcTransaction implements Transaction {

    private static final Log log = LogFactory.getLog(JdbcTransaction.class);

    protected Connection connection;
    protected DataSource dataSource;
    protected TransactionIsolationLevel level;
    protected boolean autoCommit;
    protected boolean skipSetAutoCommitOnClose;

    public JdbcTransaction(DataSource ds, TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit) {
        this(ds, desiredLevel, desiredAutoCommit, false);
    }

    public JdbcTransaction(DataSource ds, TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit,
                           boolean skipSetAutoCommitOnClose) {
        dataSource = ds;
        level = desiredLevel;
        autoCommit = desiredAutoCommit;
        this.skipSetAutoCommitOnClose = skipSetAutoCommitOnClose;
    }

    public JdbcTransaction(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null) {
            openConnection();
        }
        return connection;
    }

    @Override
    public void commit() throws SQLException {
        if (connection != null && !connection.getAutoCommit()) {
            if (log.isDebugEnabled()) {
                log.debug("Committing JDBC Connection [" + connection + "]");
            }
            connection.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (connection != null && !connection.getAutoCommit()) {
            if (log.isDebugEnabled()) {
                log.debug("Rolling back JDBC Connection [" + connection + "]");
            }
            connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null) {
            resetAutoCommit();
            if (log.isDebugEnabled()) {
                log.debug("Closing JDBC Connection [" + connection + "]");
            }
            connection.close();
        }
    }

    /**
     * 设置JDBC连接的自动提交模式。
     * 如果当前自动提交模式与期望的模式不一致，则尝试更改连接的自动提交状态。
     * 如果更改过程中出现SQLException，则抛出TransactionException异常。
     *
     * @param desiredAutoCommit 期望的自动提交模式。true表示开启自动提交，false表示关闭自动提交。
     */
    protected void setDesiredAutoCommit(boolean desiredAutoCommit) {
        try {
            // 检查当前自动提交模式是否与期望模式一致，不一致则进行设置
            if (connection.getAutoCommit() != desiredAutoCommit) {
                // 当日志级别为DEBUG时，记录设置自动提交模式的日志信息
                if (log.isDebugEnabled()) {
                    log.debug("Setting autocommit to " + desiredAutoCommit + " on JDBC Connection [" + connection + "]");
                }
                // 设置自动提交模式
                connection.setAutoCommit(desiredAutoCommit);
            }
        } catch (SQLException e) {
            // 处理设置自动提交模式时可能发生的SQLException
            // 抛出TransactionException异常，封装SQLException信息
            throw new TransactionException(
                "Error configuring AutoCommit.  " + "Your driver may not support getAutoCommit() or setAutoCommit(). "
                    + "Requested setting: " + desiredAutoCommit + ".  Cause: " + e,
                e);
        }
    }


    protected void resetAutoCommit() {
        try {
            if (!skipSetAutoCommitOnClose && !connection.getAutoCommit()) {
                // MyBatis does not call commit/rollback on a connection if just selects were performed.
                // Some databases start transactions with select statements
                // and they mandate a commit/rollback before closing the connection.
                // A workaround is setting the autocommit to true before closing the connection.
                // Sybase throws an exception here.
                if (log.isDebugEnabled()) {
                    log.debug("Resetting autocommit to true on JDBC Connection [" + connection + "]");
                }
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error resetting autocommit to true " + "before closing the connection.  Cause: " + e);
            }
        }
    }

    /**
     * 打开数据库连接。
     * 此方法会根据当前的事务级别设置连接的事务隔离级别，并设置自动提交模式为期望的自动提交状态。
     * 如果日志级别为DEBUG，会记录打开连接的日志信息。
     *
     * @throws SQLException 如果在获取数据库连接或设置事务隔离级别时发生错误
     */
    protected void openConnection() throws SQLException {
        // 当日志级别为DEBUG时，记录打开JDBC连接的信息
        if (log.isDebugEnabled()) {
            log.debug("Opening JDBC Connection");
        }
        connection = dataSource.getConnection(); // 从数据源获取数据库连接
        if (level != null) {
            // 如果存在事务级别，则设置连接的事务隔离级别
            connection.setTransactionIsolation(level.getLevel());
        }
        // 设置连接的自动提交模式为期望的自动提交状态
        setDesiredAutoCommit(autoCommit);
    }


    @Override
    public Integer getTimeout() throws SQLException {
        return null;
    }

}
