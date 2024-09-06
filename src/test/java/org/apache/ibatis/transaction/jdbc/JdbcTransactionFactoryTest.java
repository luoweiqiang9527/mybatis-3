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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcTransactionFactoryTest {

    /**
     * 测试当属性设置为null时，事务工厂是否正确处理
     * 此方法创建一个带有特定配置的测试连接，设置事务工厂的属性为null，
     * 然后创建一个事务并验证事务处理是否符合预期
     */
    @Test
    void testNullProperties() throws Exception {
        // 初始化一个测试连接，模拟自动提交为false
        TestConnection connection = new TestConnection(false);

        // 创建一个Jdbc事务工厂实例
        JdbcTransactionFactory factory = new JdbcTransactionFactory();

        // 将事务工厂的属性设置为null，以测试其处理空属性的能力
        factory.setProperties(null);

        // 使用配置好的事务工厂创建一个新的事务
        Transaction transaction = factory.newTransaction(connection);

        // 获取并使用事务中的连接
        transaction.getConnection();

        // 关闭事务，以确保资源正确释放
        transaction.close();

        // 验证连接的自动提交状态是否符合预期
        Assertions.assertTrue(connection.getAutoCommit());
    }

    /**
     * 测试在关闭事务时跳过设置自动提交模式的情况
     * 该测试用例演示了在关闭事务时，即使连接最初未设置为自动提交，
     * 通过配置skipSetAutoCommitOnClose为true，事务工厂在关闭时不会更改连接的自动提交状态
     *
     * @throws Exception 如果事务处理或模拟过程中发生错误
     */
    @Test
    void testSkipSetAutoCommitOnClose() throws Exception {
        // 创建一个初始自动提交设为false的测试连接
        TestConnection connection = new TestConnection(false);

        // 创建一个模拟的数据源，并配置其返回预先创建的测试连接
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(connection);

        // 创建并配置一个Jdbc事务工厂
        JdbcTransactionFactory factory = new JdbcTransactionFactory();
        Properties properties = new Properties();
        properties.setProperty("skipSetAutoCommitOnClose", "true");
        factory.setProperties(properties);

        // 创建一个事务，设置隔离级别为NONE，且不参与当前的事务管理
        Transaction transaction = factory.newTransaction(ds, TransactionIsolationLevel.NONE, false);

        // 获取事务连接
        transaction.getConnection();

        // 关闭事务
        transaction.close();

        // 断言连接的自动提交状态仍然为初始设置的false
        Assertions.assertFalse(connection.getAutoCommit());
    }

}
