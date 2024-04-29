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
package org.apache.ibatis.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.StringTypeHandler;
import org.junit.jupiter.api.Test;

class NullTest {

    @Test
    /**
     * 测试获取空字符串类型的JDBC类型和类型处理器。
     * 该函数不接受任何参数，也不返回任何值。
     * 主要验证 Null.STRING 常量的 JdbcType 是否为 VARCHAR，
     * 以及其类型处理器是否是 StringTypeHandler 的实例。
     */
    void shouldGetTypeAndTypeHandlerForNullStringType() {
        // 验证 Null.STRING 常量的 JDBC 类型是否为 VARCHAR
        assertEquals(JdbcType.VARCHAR, Null.STRING.getJdbcType());
        // 验证 Null.STRING 常量的类型处理器是否是 StringTypeHandler 的实例
        assertTrue(Null.STRING.getTypeHandler() instanceof StringTypeHandler);
    }


}
