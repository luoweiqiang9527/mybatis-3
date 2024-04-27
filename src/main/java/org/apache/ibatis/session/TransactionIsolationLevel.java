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
package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * 事务隔离级别
 *
 * @author Clinton Begin
 */
public enum TransactionIsolationLevel {
    // 无事务隔离级别，不支持事务
    NONE(Connection.TRANSACTION_NONE),
    // 读已提交隔离级别，保证一个事务只能读取另一个已经提交的事务所做的更改
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    // 读未提交隔离级别，允许一个事务读取另一个未提交的事务所做的更改，可能会导致脏读
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
    // 可重复读隔离级别，保证一个事务在执行过程中读取的数据是一致的，防止 phantom read（幻读）现象
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    // 串行化隔离级别，最高隔离级别，完全防止并发操作带来的数据一致性问题，但可能会导致性能下降
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE),

    /**
     * Microsoft SQL Server 的非标准隔离级别。在 SQL Server JDBC 驱动程序中定义
     * {@link com.microsoft.sqlserver.jdbc.ISQLServerConnection}
     *
     * @since 3.5.6
     */
    SQL_SERVER_SNAPSHOT(0x1000);

    private final int level;

    TransactionIsolationLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
