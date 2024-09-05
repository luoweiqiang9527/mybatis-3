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

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.reflection.ExceptionUtil;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Larry Meadors
 */
public class SqlSessionManager implements SqlSessionFactory, SqlSession {

    private final SqlSessionFactory sqlSessionFactory;
    private final SqlSession sqlSessionProxy;

    private final ThreadLocal<SqlSession> localSqlSession = new ThreadLocal<>();

    private SqlSessionManager(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.sqlSessionProxy = (SqlSession) Proxy.newProxyInstance(SqlSessionFactory.class.getClassLoader(),
            new Class[]{SqlSession.class}, new SqlSessionInterceptor());
    }

    /**
     * 创建SqlSessionManager的实例
     * 该方法使用指定的Reader对象来构建SqlSessionFactory，进而创建SqlSessionManager实例
     * 主要用于配置数据库访问的初始化，是mybatis连接数据库的重要入口之一
     *
     * @param reader Reader对象，包含数据库连接的配置信息
     * @return SqlSessionManager的实例，用于管理SQL会话
     */
    public static SqlSessionManager newInstance(Reader reader) {
        // 使用SqlSessionFactoryBuilder构建SqlSessionFactory实例
        // 然后使用新建的SqlSessionFactory创建SqlSessionManager实例并返回
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, null, null));
    }

    public static SqlSessionManager newInstance(Reader reader, String environment) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, environment, null));
    }

    public static SqlSessionManager newInstance(Reader reader, Properties properties) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(reader, null, properties));
    }

    public static SqlSessionManager newInstance(InputStream inputStream) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, null, null));
    }

    public static SqlSessionManager newInstance(InputStream inputStream, String environment) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, environment, null));
    }

    public static SqlSessionManager newInstance(InputStream inputStream, Properties properties) {
        return new SqlSessionManager(new SqlSessionFactoryBuilder().build(inputStream, null, properties));
    }

    public static SqlSessionManager newInstance(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionManager(sqlSessionFactory);
    }

    /**
     * 开始一个受管理的会话。
     * <p>
     * 本方法通过调用openSession()方法来创建一个新的会话，并将其设置到本地SqlSession对象中。
     * 这样做的目的是为了在受管理的环境中启动并维护一个数据库会话，确保会话的正确创建和使用。
     * 对于在如Spring等依赖注入框架中使用JDBC的场景，这种管理会话的方式特别有用，因为它可以帮助简化会话的生命周期管理。
     */
    public void startManagedSession() {
        this.localSqlSession.set(openSession());
    }

    public void startManagedSession(boolean autoCommit) {
        this.localSqlSession.set(openSession(autoCommit));
    }

    public void startManagedSession(Connection connection) {
        this.localSqlSession.set(openSession(connection));
    }

    public void startManagedSession(TransactionIsolationLevel level) {
        this.localSqlSession.set(openSession(level));
    }

    public void startManagedSession(ExecutorType execType) {
        this.localSqlSession.set(openSession(execType));
    }

    public void startManagedSession(ExecutorType execType, boolean autoCommit) {
        this.localSqlSession.set(openSession(execType, autoCommit));
    }

    public void startManagedSession(ExecutorType execType, TransactionIsolationLevel level) {
        this.localSqlSession.set(openSession(execType, level));
    }

    public void startManagedSession(ExecutorType execType, Connection connection) {
        this.localSqlSession.set(openSession(execType, connection));
    }

    public boolean isManagedSessionStarted() {
        return this.localSqlSession.get() != null;
    }

    /**
     * 打开一个SqlSession。
     *
     * @return 返回一个SqlSession实例，用于执行SQL操作。
     */
    @Override
    public SqlSession openSession() {
        // 调用SqlSessionFactory的openSession方法来创建并返回一个新的SqlSession实例。
        return sqlSessionFactory.openSession();
    }

    @Override
    public SqlSession openSession(boolean autoCommit) {
        return sqlSessionFactory.openSession(autoCommit);
    }

    @Override
    public SqlSession openSession(Connection connection) {
        return sqlSessionFactory.openSession(connection);
    }

    @Override
    public SqlSession openSession(TransactionIsolationLevel level) {
        return sqlSessionFactory.openSession(level);
    }

    @Override
    public SqlSession openSession(ExecutorType execType) {
        return sqlSessionFactory.openSession(execType);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
        return sqlSessionFactory.openSession(execType, autoCommit);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
        return sqlSessionFactory.openSession(execType, level);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, Connection connection) {
        return sqlSessionFactory.openSession(execType, connection);
    }

    @Override
    public Configuration getConfiguration() {
        return sqlSessionFactory.getConfiguration();
    }

    @Override
    public <T> T selectOne(String statement) {
        return sqlSessionProxy.selectOne(statement);
    }

    @Override
    public <T> T selectOne(String statement, Object parameter) {
        return sqlSessionProxy.selectOne(statement, parameter);
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
        return sqlSessionProxy.selectMap(statement, mapKey);
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
        return sqlSessionProxy.selectMap(statement, parameter, mapKey);
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
        return sqlSessionProxy.selectMap(statement, parameter, mapKey, rowBounds);
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement) {
        return sqlSessionProxy.selectCursor(statement);
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement, Object parameter) {
        return sqlSessionProxy.selectCursor(statement, parameter);
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
        return sqlSessionProxy.selectCursor(statement, parameter, rowBounds);
    }

    @Override
    public <E> List<E> selectList(String statement) {
        return sqlSessionProxy.selectList(statement);
    }

    @Override
    public <E> List<E> selectList(String statement, Object parameter) {
        return sqlSessionProxy.selectList(statement, parameter);
    }

    @Override
    public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
        return sqlSessionProxy.selectList(statement, parameter, rowBounds);
    }

    @Override
    public void select(String statement, ResultHandler handler) {
        sqlSessionProxy.select(statement, handler);
    }

    @Override
    public void select(String statement, Object parameter, ResultHandler handler) {
        sqlSessionProxy.select(statement, parameter, handler);
    }

    @Override
    public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
        sqlSessionProxy.select(statement, parameter, rowBounds, handler);
    }

    @Override
    public int insert(String statement) {
        return sqlSessionProxy.insert(statement);
    }

    @Override
    public int insert(String statement, Object parameter) {
        return sqlSessionProxy.insert(statement, parameter);
    }

    @Override
    public int update(String statement) {
        return sqlSessionProxy.update(statement);
    }

    @Override
    public int update(String statement, Object parameter) {
        return sqlSessionProxy.update(statement, parameter);
    }

    @Override
    public int delete(String statement) {
        return sqlSessionProxy.delete(statement);
    }

    @Override
    public int delete(String statement, Object parameter) {
        return sqlSessionProxy.delete(statement, parameter);
    }

    @Override
    public <T> T getMapper(Class<T> type) {
        return getConfiguration().getMapper(type, this);
    }

    @Override
    public Connection getConnection() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot get connection.  No managed session is started.");
        }
        return sqlSession.getConnection();
    }

    @Override
    public void clearCache() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot clear the cache.  No managed session is started.");
        }
        sqlSession.clearCache();
    }

    @Override
    public void commit() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot commit.  No managed session is started.");
        }
        sqlSession.commit();
    }

    @Override
    public void commit(boolean force) {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot commit.  No managed session is started.");
        }
        sqlSession.commit(force);
    }

    @Override
    public void rollback() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
        }
        sqlSession.rollback();
    }

    @Override
    public void rollback(boolean force) {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
        }
        sqlSession.rollback(force);
    }

    @Override
    public List<BatchResult> flushStatements() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot rollback.  No managed session is started.");
        }
        return sqlSession.flushStatements();
    }

    @Override
    public void close() {
        final SqlSession sqlSession = localSqlSession.get();
        if (sqlSession == null) {
            throw new SqlSessionException("Error:  Cannot close.  No managed session is started.");
        }
        try {
            sqlSession.close();
        } finally {
            localSqlSession.remove();
        }
    }

    private class SqlSessionInterceptor implements InvocationHandler {
        public SqlSessionInterceptor() {
            // Prevent Synthetic Access
        }

        /**
         * 动态代理方法调用
         * 当调用代理对象的方法时，此方法会被触发
         * 主要功能是管理SqlSession的生命周期，确保每个方法调用都在有效的SqlSession内执行
         *
         * @param proxy  代理对象本身，用于反射调用
         * @param method 被调用的方法对象
         * @param args   方法调用时传递的参数数组
         * @return 方法的执行结果
         * @throws Throwable 如果方法调用中出现异常，则抛出
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 尝试从线程本地获取当前SqlSession
            final SqlSession sqlSession = SqlSessionManager.this.localSqlSession.get();
            // 如果SqlSession存在
            if (sqlSession != null) {
                try {
                    // 直接在当前SqlSession上执行方法
                    return method.invoke(sqlSession, args);
                } catch (Throwable t) {
                    // 如果执行中出现异常，包装并抛出
                    throw ExceptionUtil.unwrapThrowable(t);
                }
            }
            // 如果当前线程没有SqlSession，创建一个新的
            try (SqlSession autoSqlSession = openSession()) {
                try {
                    // 为什么不在此处设置SqlSession？
                    // SqlSessionManager.this.localSqlSession.set(autoSqlSession);
                    // 在新的SqlSession中执行方法
                    final Object result = method.invoke(autoSqlSession, args);
                    // 如果执行成功，提交事务
                    autoSqlSession.commit();
                    return result;
                } catch (Throwable t) {
                    // 如果执行中出现异常，回滚事务
                    autoSqlSession.rollback();
                    // 包装并抛出异常
                    throw ExceptionUtil.unwrapThrowable(t);
                }
            }
        }

    }

}
