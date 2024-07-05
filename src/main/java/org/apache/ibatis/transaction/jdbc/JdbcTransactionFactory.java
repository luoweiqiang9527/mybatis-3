/*
 *    Copyright 2009-2022 the original author or authors.
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
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

/**
 * Creates {@link JdbcTransaction} instances.
 *
 * @author Clinton Begin
 * @see JdbcTransaction
 */
public class JdbcTransactionFactory implements TransactionFactory {
    /**
     * 例如：mysql默认开启事务自动提交模式，每条sql语句都会被当做一个单独的事务自动执行。
     * 但是在某些情况下，我们需要关闭事务自动提交来保证数据的一致性。
     * 1. 复杂业务操作：一系列数据库操作作为一个整体
     * 2. 长事务处理：大量数据处理或复杂的查询操作时
     * 3. 维护数据库完整性：
     * 4. 性能优化：批量处理多条sql语句并在所有操作后一次性提交事务，可以减少事务开销。
     * 5. 并发控制：在高并发环境下，为了减少锁的持有时间，提高系统的并发处理能力，可以通过手动控制事务的开始和结束来精细管理资源锁定。
     */
    private boolean skipSetAutoCommitOnClose;

    @Override
    public void setProperties(Properties props) {
        if (props == null) {
            return;
        }
        String value = props.getProperty("skipSetAutoCommitOnClose");
        if (value != null) {
            skipSetAutoCommitOnClose = Boolean.parseBoolean(value);
        }
    }

    @Override
    public Transaction newTransaction(Connection conn) {
        return new JdbcTransaction(conn);
    }

    @Override
    public Transaction newTransaction(DataSource ds, TransactionIsolationLevel level, boolean autoCommit) {
        return new JdbcTransaction(ds, level, autoCommit, skipSetAutoCommitOnClose);
    }
}
