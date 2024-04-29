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
package org.apache.ibatis.jdbc;

import org.apache.ibatis.type.BigDecimalTypeHandler;
import org.apache.ibatis.type.BlobTypeHandler;
import org.apache.ibatis.type.BooleanTypeHandler;
import org.apache.ibatis.type.ByteArrayTypeHandler;
import org.apache.ibatis.type.ByteTypeHandler;
import org.apache.ibatis.type.ClobTypeHandler;
import org.apache.ibatis.type.DateOnlyTypeHandler;
import org.apache.ibatis.type.DateTypeHandler;
import org.apache.ibatis.type.DoubleTypeHandler;
import org.apache.ibatis.type.FloatTypeHandler;
import org.apache.ibatis.type.IntegerTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.LongTypeHandler;
import org.apache.ibatis.type.ObjectTypeHandler;
import org.apache.ibatis.type.ShortTypeHandler;
import org.apache.ibatis.type.SqlDateTypeHandler;
import org.apache.ibatis.type.SqlTimeTypeHandler;
import org.apache.ibatis.type.SqlTimestampTypeHandler;
import org.apache.ibatis.type.StringTypeHandler;
import org.apache.ibatis.type.TimeOnlyTypeHandler;
import org.apache.ibatis.type.TypeHandler;

/**
 * Null枚举类，提供了与各种Java类型和JDBC类型对应的关系，以及相应的TypeHandler。
 * 这使得在处理NULL值时，能够方便地根据需要的类型获取相应的TypeHandler和JdbcType。
 *
 * @author Clinton Begin
 * @author Adam Gent
 */
public enum Null {
    // 下面是各个枚举值，每个枚举值都对应一个Java类型和一个JDBC类型，以及相应的TypeHandler。
    BOOLEAN(new BooleanTypeHandler(), JdbcType.BOOLEAN),

    BYTE(new ByteTypeHandler(), JdbcType.TINYINT),

    SHORT(new ShortTypeHandler(), JdbcType.SMALLINT),

    INTEGER(new IntegerTypeHandler(), JdbcType.INTEGER),

    LONG(new LongTypeHandler(), JdbcType.BIGINT),

    FLOAT(new FloatTypeHandler(), JdbcType.FLOAT),

    DOUBLE(new DoubleTypeHandler(), JdbcType.DOUBLE),

    BIGDECIMAL(new BigDecimalTypeHandler(), JdbcType.DECIMAL),

    STRING(new StringTypeHandler(), JdbcType.VARCHAR),

    CLOB(new ClobTypeHandler(), JdbcType.CLOB),

    LONGVARCHAR(new ClobTypeHandler(), JdbcType.LONGVARCHAR),

    BYTEARRAY(new ByteArrayTypeHandler(), JdbcType.LONGVARBINARY),

    BLOB(new BlobTypeHandler(), JdbcType.BLOB),

    LONGVARBINARY(new BlobTypeHandler(), JdbcType.LONGVARBINARY),

    OBJECT(new ObjectTypeHandler(), JdbcType.OTHER),

    OTHER(new ObjectTypeHandler(), JdbcType.OTHER),

    TIMESTAMP(new DateTypeHandler(), JdbcType.TIMESTAMP),

    DATE(new DateOnlyTypeHandler(), JdbcType.DATE),

    TIME(new TimeOnlyTypeHandler(), JdbcType.TIME),

    SQLTIMESTAMP(new SqlTimestampTypeHandler(), JdbcType.TIMESTAMP),

    SQLDATE(new SqlDateTypeHandler(), JdbcType.DATE),

    SQLTIME(new SqlTimeTypeHandler(), JdbcType.TIME);
    // 每个枚举值都包含一个TypeHandler实例和一个对应的JdbcType。
    private final TypeHandler<?> typeHandler;
    private final JdbcType jdbcType;

    /**
     * 构造函数，为每个枚举值初始化TypeHandler和JdbcType。
     *
     * @param typeHandler 该枚举值对应的TypeHandler
     * @param jdbcType    该枚举值对应的JDBC类型
     */
    Null(TypeHandler<?> typeHandler, JdbcType jdbcType) {
        this.typeHandler = typeHandler;
        this.jdbcType = jdbcType;
    }

    /**
     * 获取该枚举值对应的TypeHandler。
     *
     * @return 对应的TypeHandler实例
     */
    public TypeHandler<?> getTypeHandler() {
        return typeHandler;
    }

    /**
     * 获取该枚举值对应的JdbcType。
     *
     * @return 对应的JdbcType枚举值
     */
    public JdbcType getJdbcType() {
        return jdbcType;
    }
}
