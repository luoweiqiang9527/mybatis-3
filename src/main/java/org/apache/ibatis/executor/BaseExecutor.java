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
package org.apache.ibatis.executor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

/**
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

    private static final Log log = LogFactory.getLog(BaseExecutor.class);
    // 事务处理对象，用于管理数据库操作的事务。
    protected Transaction transaction;
    // 执行器对象，用于异步任务的执行。
    protected Executor wrapper;
    // 延迟加载队列，存放需要延迟加载的数据。
    protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
    // 本地缓存，用于存储经常访问的数据，提高访问效率。
    protected PerpetualCache localCache;
    // 本地输出参数缓存，用于存储函数或存储过程的输出参数。
    protected PerpetualCache localOutputParameterCache;
    // 配置对象，用于访问应用程序的配置信息。
    protected Configuration configuration;
    // 查询堆栈计数器，用于追踪当前执行的查询深度。
    protected int queryStack;
    // 标记当前对象是否已关闭，用于控制对象的生命周期。
    private boolean closed;

    protected BaseExecutor(Configuration configuration, Transaction transaction) {
        this.transaction = transaction;
        this.deferredLoads = new ConcurrentLinkedQueue<>();
        this.localCache = new PerpetualCache("LocalCache");
        this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
        this.closed = false;
        this.configuration = configuration;
        this.wrapper = this;
    }

    @Override
    /**
     * 获取当前的事务对象。
     *
     * @return Transaction 返回当前的事务对象。
     * @throws ExecutorException 如果执行器已关闭，则抛出执行器关闭异常。
     */
    public Transaction getTransaction() {
        // 检查执行器是否已关闭，若已关闭，则抛出异常
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        return transaction; // 返回当前事务对象
    }


    @Override
    /**
     * 关闭事务及其相关资源。
     * 如果指定强制回滚，将尝试进行回滚操作。
     * 不论回滚操作是否成功，都会尝试关闭事务对象。
     * 在关闭过程中捕获的任何SQLException都将被记录警告，不会对外抛出。
     * 最后，清理相关资源并标记此对象为已关闭。
     *
     * @param forceRollback 如果为true，则强制进行回滚操作。
     */
    public void close(boolean forceRollback) {
        try {
            try {
                // 尝试进行回滚操作，根据参数决定是否强制回滚。
                rollback(forceRollback);
            } finally {
                // 无论回滚操作成功与否，都尝试关闭事务对象。
                if (transaction != null) {
                    transaction.close();
                }
            }
        } catch (SQLException e) {
            // 捕获并记录SQL异常，此时已关闭状态，不再进行处理。
            log.warn("Unexpected exception on closing transaction.  Cause: " + e);
        } finally {
            // 清理资源并标记关闭状态。
            transaction = null;
            deferredLoads = null;
            localCache = null;
            localOutputParameterCache = null;
            closed = true;
        }
    }


    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    /**
     * 更新操作的执行方法。
     *
     * @param ms MappedStatement对象，代表一个SQL语句的映射配置。
     * @param parameter 执行更新操作时需要的参数。
     * @return 更新操作影响的行数。
     * @throws SQLException 如果执行更新操作时发生SQL错误。
     */
    public int update(MappedStatement ms, Object parameter) throws SQLException {
        // 初始化错误上下文，记录执行更新操作的资源、活动和对象标识符
        ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());

        // 检查执行器是否已关闭，如果已关闭，则抛出异常
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }

        // 清除本地缓存
        clearLocalCache();

        // 执行更新操作，并返回影响的行数
        return doUpdate(ms, parameter);
    }


    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        return flushStatements(false);
    }

    public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        return doFlushStatements(isRollBack);
    }

    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler)
        throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
        return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * 执行查询操作。
     *
     * @param ms MappedStatement，代表一个映射语句，用于定义如何从数据库获取数据。
     * @param parameter 参数对象，用于查询时传递的参数。
     * @param rowBounds 行限制，用于指定查询结果的起始位置和返回行数。
     * @param resultHandler 结果处理器，用于自定义结果集的处理方式。
     * @param key 缓存键，用于标识查询结果在缓存中的位置。
     * @param boundSql 绑定的SQL对象，包含实际执行的SQL语句及其参数。
     * @return 返回查询结果列表。
     * @throws SQLException 如果执行查询时发生SQL错误。
     */
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
                             CacheKey key, BoundSql boundSql) throws SQLException {
        // 初始化错误上下文
        ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());

        // 检查执行器是否已关闭
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }

        // 如果当前没有查询在执行并且要求刷新缓存，则清除本地缓存
        if (queryStack == 0 && ms.isFlushCacheRequired()) {
            clearLocalCache();
        }

        List<E> list;
        try {
            // 查询计数加一，用于缓存和事务性上下文的管理
            queryStack++;
            // 尝试从缓存中获取查询结果
            list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
            if (list != null) {
                // 处理本地缓存中存储的输出参数
                handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
            } else {
                // 从数据库中执行查询
                list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
            }
        } finally {
            // 查询计数减一，确保查询计数器正确恢复
            queryStack--;
        }

        // 如果当前没有查询在执行，进行后续清理和缓存更新
        if (queryStack == 0) {
            // 处理延迟加载
            for (DeferredLoad deferredLoad : deferredLoads) {
                deferredLoad.load();
            }
            deferredLoads.clear(); // 清空延迟加载列表
            // 根据配置，可能需要清除本地缓存
            if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
                clearLocalCache();
            }
        }

        return list; // 返回查询结果
    }


    @Override
    public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        return doQueryCursor(ms, parameter, rowBounds, boundSql);
    }

    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key,
                          Class<?> targetType) {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
        if (deferredLoad.canLoad()) {
            deferredLoad.load();
        } else {
            deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
        }
    }

    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        // 检查executor是否已关闭，避免在关闭后执行操作
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        // 创建缓存键对象，用于后续的缓存存储
        CacheKey cacheKey = new CacheKey();
        // 将MappedStatement的ID更新到缓存键中，作为缓存键的一部分
        cacheKey.update(ms.getId());
        // 将RowBounds的偏移量更新到缓存键中，用于分页查询的区分
        cacheKey.update(rowBounds.getOffset());
        // 将RowBounds的限制更新到缓存键中，用于分页查询的区分
        cacheKey.update(rowBounds.getLimit());
        // 将BoundSql的SQL语句更新到缓存键中，用于查询结果的区分
        cacheKey.update(boundSql.getSql());
        // 获取BoundSql的参数映射列表，用于处理查询参数
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        // 获取类型处理器注册表，用于处理不同类型的参数对象
        TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
        // 模拟DefaultParameterHandler逻辑，用于遍历参数映射并更新缓存键
        MetaObject metaObject = null;
        for (ParameterMapping parameterMapping : parameterMappings) {
            // 只处理非输出参数模式的参数映射
            if (parameterMapping.getMode() != ParameterMode.OUT) {
                Object value;
                String propertyName = parameterMapping.getProperty();
                // 如果BoundSql包含附加参数，则获取该参数值
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameterObject == null) {
                    // 如果参数对象为空，则值为null
                    value = null;
                } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                    // 如果参数对象的类有相应的类型处理器，则值为参数对象本身
                    value = parameterObject;
                } else {
                    // 否则，通过MetaObject获取属性值
                    if (metaObject == null) {
                        metaObject = configuration.newMetaObject(parameterObject);
                    }
                    value = metaObject.getValue(propertyName);
                }
                // 将参数值更新到缓存键中
                cacheKey.update(value);
            }
        }
        // 如果配置的环境不为空，则将环境ID更新到缓存键中，用于区分不同环境下的缓存
        if (configuration.getEnvironment() != null) {
            cacheKey.update(configuration.getEnvironment().getId());
        }
        // 返回创建的缓存键对象
        return cacheKey;
    }

    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return localCache.getObject(key) != null;
    }

    @Override
    public void commit(boolean required) throws SQLException {
        if (closed) {
            throw new ExecutorException("Cannot commit, transaction is already closed");
        }
        clearLocalCache();
        flushStatements();
        if (required) {
            transaction.commit();
        }
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        if (!closed) {
            try {
                clearLocalCache();
                flushStatements(true);
            } finally {
                if (required) {
                    transaction.rollback();
                }
            }
        }
    }

    @Override
    public void clearLocalCache() {
        if (!closed) {
            localCache.clear();
            localOutputParameterCache.clear();
        }
    }

    protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

    protected abstract List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException;

    protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                           ResultHandler resultHandler, BoundSql boundSql) throws SQLException;

    protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                                   BoundSql boundSql) throws SQLException;

    protected void closeStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /**
     * Apply a transaction timeout.
     *
     * @param statement a current statement
     * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
     * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
     * @since 3.4.0
     */
    protected void applyTransactionTimeout(Statement statement) throws SQLException {
        StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
    }

    private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter,
                                                     BoundSql boundSql) {
        if (ms.getStatementType() == StatementType.CALLABLE) {
            final Object cachedParameter = localOutputParameterCache.getObject(key);
            if (cachedParameter != null && parameter != null) {
                final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
                final MetaObject metaParameter = configuration.newMetaObject(parameter);
                for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                    if (parameterMapping.getMode() != ParameterMode.IN) {
                        final String parameterName = parameterMapping.getProperty();
                        final Object cachedValue = metaCachedParameter.getValue(parameterName);
                        metaParameter.setValue(parameterName, cachedValue);
                    }
                }
            }
        }
    }

    /**
     * 从数据库查询数据的通用方法
     * <p>
     * 本方法主要用于从数据库执行查询操作，并将结果缓存起来
     * 它处理了参数映射、结果转换等复杂逻辑，并考虑了存储过程（Callable Statement）的场景
     *
     * @param <E>           泛型标记，表示查询结果的元素类型
     * @param ms            MappedStatement对象，包含SQL映射信息
     * @param parameter     查询参数
     * @param rowBounds     分页信息
     * @param resultHandler 结果处理器
     * @param key           缓存键，用于标识查询结果
     * @param boundSql      预编译的SQL对象，包含SQL语句和参数映射信息
     * @return 查询结果的列表
     * @throws SQLException 如果查询过程中发生SQL错误
     */
    private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                          ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        List<E> list;
        // 将占位符放入本地缓存，用于标记正在执行的查询
        localCache.putObject(key, EXECUTION_PLACEHOLDER);
        try {
            // 执行实际的数据库查询操作
            list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
        } finally {
            // 查询完成后，移除本地缓存中的占位符
            localCache.removeObject(key);
        }
        // 将查询结果存入本地缓存
        localCache.putObject(key, list);
        // 如果查询语句类型为Callable，则将输出参数存入本地缓存
        if (ms.getStatementType() == StatementType.CALLABLE) {
            localOutputParameterCache.putObject(key, parameter);
        }
        // 返回查询结果
        return list;
    }

    protected Connection getConnection(Log statementLog) throws SQLException {
        Connection connection = transaction.getConnection();
        if (statementLog.isDebugEnabled()) {
            return ConnectionLogger.newInstance(connection, statementLog, queryStack);
        }
        return connection;
    }

    @Override
    public void setExecutorWrapper(Executor wrapper) {
        this.wrapper = wrapper;
    }

    private static class DeferredLoad {

        private final MetaObject resultObject;
        private final String property;
        private final Class<?> targetType;
        private final CacheKey key;
        private final PerpetualCache localCache;
        private final ObjectFactory objectFactory;
        private final ResultExtractor resultExtractor;

        // issue #781
        public DeferredLoad(MetaObject resultObject, String property, CacheKey key, PerpetualCache localCache,
                            Configuration configuration, Class<?> targetType) {
            this.resultObject = resultObject;
            this.property = property;
            this.key = key;
            this.localCache = localCache;
            this.objectFactory = configuration.getObjectFactory();
            this.resultExtractor = new ResultExtractor(configuration, objectFactory);
            this.targetType = targetType;
        }

        public boolean canLoad() {
            return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
        }

        public void load() {
            @SuppressWarnings("unchecked")
            // we suppose we get back a List
            List<Object> list = (List<Object>) localCache.getObject(key);
            Object value = resultExtractor.extractObjectFromList(list, targetType);
            resultObject.setValue(property, value);
        }

    }

}
