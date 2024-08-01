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
package org.apache.ibatis.type;

import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.session.Configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用于引用泛型类型的基 {@link TypeHandler}。
 * <p>
 * 重要提示: 从 3.5.0, 该类不再调用 {@link ResultSet#wasNull()} 和 {@link CallableStatement#wasNull()}处理SQL {@code NULL}值。
 * 换句话说, {@code null} 值由子类进行处理。
 * </p>
 *
 * @author Clinton Begin
 * @author Simone Tripodi
 * @author Kzuki Shimizu
 */
public abstract class BaseTypeHandler<T> extends TypeReference<T> implements TypeHandler<T> {

    /**
     * @deprecated Since 3.5.0 - See https://github.com/mybatis/mybatis-3/issues/1203. This field will remove future.
     */
    @Deprecated
    protected Configuration configuration;

    /**
     * Sets the configuration.
     *
     * @param c the new configuration
     * @deprecated Since 3.5.0 - See https://github.com/mybatis/mybatis-3/issues/1203. This property will remove future.
     */
    @Deprecated
    public void setConfiguration(Configuration c) {
        this.configuration = c;
    }

    @Override
    /**
     * 设置PreparedStatement的参数。
     *
     * @param ps PreparedStatement对象，待设置参数的预编译语句。
     * @param i 参数在PreparedStatement中的位置。
     * @param parameter 要设置的参数值。
     * @param jdbcType 参数对应的JdbcType类型。如果参数为null，此值必须指定。
     * @throws SQLException 如果设置参数时发生SQL异常。
     * @throws TypeException 如果无法设置参数，或者JdbcType未指定（当参数为null时）。
     */
    public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            // 当参数为null时，必须指定JdbcType
            if (jdbcType == null) {
                // 如果参数为null，则必须指定JdbcType
                throw new TypeException("JDBC requires that the JdbcType must be specified for all nullable parameters.");
            }
            try {
                // 尝试使用指定的JdbcType设置null参数
                ps.setNull(i, jdbcType.TYPE_CODE);
            } catch (SQLException e) {
                // 如果设置null参数失败，则抛出异常
                throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . "
                    + "Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. "
                    + "Cause: " + e, e);
            }
        } else {
            try {
                // 设置非null参数，由子类实现
                setNonNullParameter(ps, i, parameter, jdbcType);
            } catch (Exception e) {
                throw new TypeException("Error setting non null for parameter #" + i + " with JdbcType " + jdbcType + " . "
                    + "Try setting a different JdbcType for this parameter or a different configuration property. " + "Cause: "
                    + e, e);
            }
        }
    }


    @Override
    public T getResult(ResultSet rs, String columnName) throws SQLException {
        try {
            return getNullableResult(rs, columnName);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column '" + columnName + "' from result set.  Cause: " + e,
                e);
        }
    }

    @Override
    public T getResult(ResultSet rs, int columnIndex) throws SQLException {
        try {
            return getNullableResult(rs, columnIndex);
        } catch (Exception e) {
            throw new ResultMapException("Error attempting to get column #" + columnIndex + " from result set.  Cause: " + e,
                e);
        }
    }

    @Override
    public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
        try {
            return getNullableResult(cs, columnIndex);
        } catch (Exception e) {
            throw new ResultMapException(
                "Error attempting to get column #" + columnIndex + " from callable statement.  Cause: " + e, e);
        }
    }

    public abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType)
        throws SQLException;

    /**
     * Gets the nullable result.
     *
     * @param rs         the rs
     * @param columnName Column name, when configuration <code>useColumnLabel</code> is <code>false</code>
     * @return the nullable result
     * @throws SQLException the SQL exception
     */
    public abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;

    public abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;

    public abstract T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException;

}
