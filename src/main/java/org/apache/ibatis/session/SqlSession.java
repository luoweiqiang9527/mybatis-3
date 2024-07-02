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

import java.io.Closeable;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;

/**
 * 用于处理 MyBatis 的主要 Java 接口。
 * 通过这个接口，你可以执行命令，获取映射器和管理事务。
 *
 * @author Clinton Begin
 */
public interface SqlSession extends Closeable {

    /**
     * 检索从语句键映射的单行。
     *
     * @param <T>       the returned object type
     * @param statement the statement
     * @return Mapped object
     */
    <T> T selectOne(String statement);

    /**
     * 检索从语句键和参数映射的单行。
     *
     * @param <T>       the returned object type
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @return Mapped object
     */
    <T> T selectOne(String statement, Object parameter);

    /**
     * 从语句键中检索映射对象的列表。
     *
     * @param <E>       the returned list element type
     * @param statement 与要使用的语句匹配的唯一标识符。
     * @return List of mapped object
     */
    <E> List<E> selectList(String statement);

    /**
     * 从语句键和参数中检索映射对象的列表。
     *
     * @param <E>       the returned list element type
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @return List of mapped object
     */
    <E> List<E> selectList(String statement, Object parameter);

    /**
     * 在指定的行边界内，从语句键和参数中检索映射对象的列表。
     *
     * @param <E>       the returned list element type
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @param rowBounds Bounds to limit object retrieval
     * @return List of mapped object
     */
    <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds);

    /**
     * selectMap 是一种特例，因为它旨在将结果列表转换为基于生成对象中的属性。
     * Eg. Return a of Map[Integer,Author] for selectMap("selectAuthors","id")
     *
     * @param <K>       the returned Map keys type
     * @param <V>       the returned Map values type
     * @param statement Unique identifier matching the statement to use.
     * @param mapKey    The property to use as key for each value in the list.
     * @return Map containing key pair data.
     */
    <K, V> Map<K, V> selectMap(String statement, String mapKey);

    /**
     * selectMap 是一种特例，因为它旨在将结果列表转换为基于
     * 生成对象中的属性。
     *
     * @param <K>       the returned Map keys type
     * @param <V>       the returned Map values type
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @param mapKey    The property to use as key for each value in the list.
     * @return Map containing key pair data.
     */
    <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey);

    /**
     * selectMap 是一种特例，因为它旨在将结果列表转换为基于
     * 生成对象中的属性。
     *
     * @param <K>       the returned Map keys type
     * @param <V>       the returned Map values type
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @param mapKey    The property to use as key for each value in the list.
     * @param rowBounds Bounds to limit object retrieval
     * @return Map containing key pair data.
     */
    <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds);

    /**
     * Cursor 提供与 List 相同的结果，只是它使用迭代器延迟获取数据。
     *
     * @param <T>       the returned cursor element type.
     * @param statement Unique identifier matching the statement to use.
     * @return Cursor of mapped objects
     */
    <T> Cursor<T> selectCursor(String statement);

    /**
     * Cursor 提供与 List 相同的结果，只是它使用迭代器延迟获取数据。
     *
     * @param <T>       the returned cursor element type.
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @return Cursor of mapped objects
     */
    <T> Cursor<T> selectCursor(String statement, Object parameter);

    /**
     * Cursor 提供与 List 相同的结果，只是它使用迭代器延迟获取数据。
     *
     * @param <T>       the returned cursor element type.
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @param rowBounds Bounds to limit object retrieval
     * @return Cursor of mapped objects
     */
    <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds);

    /**
     * 使用 {@code ResultHandler} 检索从语句键和参数映射的单行。
     *
     * @param statement Unique identifier matching the statement to use.
     * @param parameter A parameter object to pass to the statement.
     * @param handler   将处理每个检索到的行的 ResultHandler
     */
    void select(String statement, Object parameter, ResultHandler handler);

    /**
     * 使用 {@code ResultHandler} 检索从语句映射的单行。
     *
     * @param statement Unique identifier matching the statement to use.
     * @param handler   ResultHandler that will handle each retrieved row
     */
    void select(String statement, ResultHandler handler);

    /**
     * 使用 {@code ResultHandler} 检索从语句键和参数映射的单行，并
     * {@code RowBounds}。
     *
     * @param statement Unique identifier matching the statement to use.
     * @param parameter the parameter
     * @param rowBounds RowBound instance to limit the query results
     * @param handler   ResultHandler that will handle each retrieved row
     */
    void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler);

    /**
     * 执行 insert 语句。
     *
     * @param statement Unique identifier matching the statement to execute.
     * @return int The number of rows affected by the insert.
     */
    int insert(String statement);

    /**
     * 使用给定的参数对象执行 insert 语句。任何生成的自动增量值或 selectKey
     * 条目将修改给定的参数对象属性。仅返回受影响的行数。
     *
     * @param statement Unique identifier matching the statement to execute.
     * @param parameter A parameter object to pass to the statement.
     * @return int The number of rows affected by the insert.
     */
    int insert(String statement, Object parameter);

    /**
     * 执行 update 语句。将返回受影响的行数。
     *
     * @param statement Unique identifier matching the statement to execute.
     * @return int The number of rows affected by the update.
     */
    int update(String statement);

    /**
     * 执行 update 语句。将返回受影响的行数。
     *
     * @param statement Unique identifier matching the statement to execute.
     * @param parameter A parameter object to pass to the statement.
     * @return int The number of rows affected by the update.
     */
    int update(String statement, Object parameter);

    /**
     * 执行 delete 语句。将返回受影响的行数。
     *
     * @param statement Unique identifier matching the statement to execute.
     * @return int The number of rows affected by the delete.
     */
    int delete(String statement);

    /**
     * 执行 delete 语句。将返回受影响的行数。
     *
     * @param statement Unique identifier matching the statement to execute.
     * @param parameter A parameter object to pass to the statement.
     * @return int The number of rows affected by the delete.
     */
    int delete(String statement, Object parameter);

    /**
     * 刷新批处理语句并提交数据库连接。请注意，如果没有，则不会提交数据库连接
     * 更新/删除/插入已调用。强制提交调用 {@link SqlSession#commit（boolean）}
     */
    void commit();

    /**
     * 刷新批处理语句并提交数据库连接。
     *
     * @param force forces connection commit
     */
    void commit(boolean force);

    /**
     * 丢弃挂起的批处理语句并回滚数据库连接。请注意，数据库连接不会
     * 如果未调用更新/删除/插入，则回滚。强制回滚调用
     * {@link SqlSession#rollback（boolean）}
     */
    void rollback();

    /**
     * 丢弃挂起的批处理语句并回滚数据库连接。请注意，数据库连接不会
     * 如果未调用更新/删除/插入，则回滚。
     *
     * @param force forces connection rollback
     */
    void rollback(boolean force);

    /**
     * 刷新批处理语句。
     *
     * @return BatchResult list of updated records
     * @since 3.0.6
     */
    List<BatchResult> flushStatements();

    /**
     * 关闭会话。
     */
    @Override
    void close();

    /**
     * 清除本地会话缓存。
     */
    void clearCache();

    /**
     * 检索当前配置。
     *
     * @return Configuration
     */
    Configuration getConfiguration();

    /**
     * 检索映射器。
     *
     * @param <T>  the mapper type
     * @param type Mapper interface class
     * @return a mapper bound to this SqlSession
     */
    <T> T getMapper(Class<T> type);

    /**
     * 检索内部数据库连接。
     *
     * @return Connection
     */
    Connection getConnection();
}
