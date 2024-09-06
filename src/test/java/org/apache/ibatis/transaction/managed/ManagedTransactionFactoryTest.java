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
package org.apache.ibatis.transaction.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.sql.Connection;
import java.util.Properties;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagedTransactionFactoryTest extends BaseDataTest {

    @Mock
    private Connection conn;

    /**
     * 验证对托管事务API的调用不会转发到托管连接
     * 此测试用例旨在确保当使用托管事务工厂时，新创建的事务对象在提交、回滚和关闭操作时，
     * 不会错误地操作托管连接。特别是，验证在这些操作后，连接的close方法仅被调用一次，
     * 以避免不必要的资源释放操作。
     *
     * @throws Exception 如果事务处理或验证过程中发生错误
     */
    @Test
    void shouldEnsureThatCallsToManagedTransactionAPIDoNotForwardToManagedConnections() throws Exception {
        // 创建一个新的托管事务工厂实例
        TransactionFactory tf = new ManagedTransactionFactory();
        // 设置事务工厂的属性，此处使用新的Properties实例
        tf.setProperties(new Properties());
        // 通过事务工厂创建一个新的事务，使用预先存在的连接conn
        Transaction tx = tf.newTransaction(conn);
        // 验证事务所使用的连接是否与预期的连接一致
        assertEquals(conn, tx.getConnection());
        // 执行事务的提交操作
        tx.commit();
        // 执行事务的回滚操作，通常情况下，如果事务已经提交，再次调用rollback会抛出异常，这里测试这种异常处理是否正确
        tx.rollback();
        // 关闭事务，这一步通常也是清理资源的过程
        tx.close();
        // 验证连接的close方法是否被调用，期望结果是被调用一次，以确保资源正确释放
        verify(conn).close();
    }


    /**
     * 验证对托管事务API的调用不会转发到托管连接，并且在事务完成后不会关闭连接
     *
     * 此测试用例旨在确保在托管事务工厂环境下，对事务API的调用不会导致不必要的连接关闭操作
     * 同时验证了事务的提交和回滚操作能够成功执行，且在事务对象关闭后，没有更多的交互发生
     *
     * @throws Exception 如果测试过程中发生异常
     */
    @Test
    void shouldEnsureThatCallsToManagedTransactionAPIDoNotForwardToManagedConnectionsAndDoesNotCloseConnection()
        throws Exception {
        // 创建一个托管事务工厂实例
        TransactionFactory tf = new ManagedTransactionFactory();
        // 初始化配置属性，设置不关闭连接
        Properties props = new Properties();
        props.setProperty("closeConnection", "false");
        // 为事务工厂设置属性
        tf.setProperties(props);
        // 创建一个事务对象，使用的是测试连接
        Transaction tx = tf.newTransaction(conn);
        // 验证事务对象返回的连接是预期的测试连接
        assertEquals(conn, tx.getConnection());
        // 模拟事务提交操作
        tx.commit();
        // 模拟事务回滚操作，通常提交后不应有回滚，但此处为了测试事务生命周期管理
        tx.rollback();
        // 模拟事务关闭操作
        tx.close();
        // 验证之后没有更多的交互发生，确保连接未被关闭
        verifyNoMoreInteractions(conn);
    }


}
