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

import org.apache.ibatis.session.TransactionIsolationLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTransactionTest {
    /**
     * 测试在关闭时自动提交的不同场景
     * 该方法通过调用testAutoCommit方法，传入不同的参数组合来测试在不同情况下的自动提交行为
     * 参数的含义为：
     * 第一个参数：初始时是否自动提交
     * 第二个参数：是否关闭自动提交
     * 第三个参数：期望的自动提交状态
     * 第四个参数：是否期望抛出异常
     * 通过这些测试用例，可以确保在各种配置场景下，自动提交功能的行为符合预期
     */
    @Test
    void testSetAutoCommitOnClose() throws Exception {
        // skip setAutoCommitOnClose 设置关闭连接时设置自动提交事务为false,默认为自动提交事务。
        testAutoCommit(true, false, true, false);
        testAutoCommit(false, false, true, false);
        testAutoCommit(true, true, true, false);
        testAutoCommit(false, true, true, false);
        // skip setAutoCommitOnClose 设置关闭连接时设置自动提交事务为true,最终的自动提交状态以desiredAutoCommit为准
        testAutoCommit(true, false, false, true);
        testAutoCommit(false, false, false, true);
        testAutoCommit(true, true, true, true);
        testAutoCommit(false, true, true, true);
    }

    /**
     * 测试JdbcTransaction在不同情况下的自动提交行为
     * 此方法用于验证在给定初始自动提交状态、期望的自动提交状态和关闭时是否跳过设置自动提交状态的情况下，实际的自动提交状态是否符合期望
     *
     * @param initialAutoCommit        数据库连接的初始自动提交状态
     * @param desiredAutoCommit        期望的自动提交状态，即调用setAutoCommit方法时设置的值
     * @param resultAutoCommit         预期在事务处理过程结束后的实际自动提交状态
     * @param skipSetAutoCommitOnClose 如果为true，则在关闭连接时不应调用setAutoCommit方法这是为了测试是否正确处理了此行为
     * @throws Exception 如果测试过程中发生错误，则抛出异常
     */
    private void testAutoCommit(boolean initialAutoCommit, boolean desiredAutoCommit, boolean resultAutoCommit,
                                boolean skipSetAutoCommitOnClose) throws Exception {
        // 创建一个带有指定初始自动提交状态的测试连接
        TestConnection con = new TestConnection(initialAutoCommit);

        // 创建一个模拟的数据源，并配置它在调用getConnection方法时返回之前创建的测试连接
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(con);

        // 创建一个JdbcTransaction实例，传入数据源、事务隔离级别、期望的自动提交状态以及关闭时是否跳过设置自动提交状态的标志
        JdbcTransaction transaction = new JdbcTransaction(ds, TransactionIsolationLevel.NONE, desiredAutoCommit,
            skipSetAutoCommitOnClose);

        // 获取数据库连接
        transaction.getConnection();

        // 提交事务
        transaction.commit();

        // 关闭事务
        transaction.close();

        // 验证连接的自动提交状态是否与预期相符
        Assertions.assertEquals(resultAutoCommit, con.getAutoCommit());
    }
}
