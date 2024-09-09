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

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * Specify the behavior when detects an unknown column (or unknown property type) of automatic mapping target.
 * 指定检测到自动映射目标的未知列（或未知属性类型）时的行为。
 *
 * @author Kazuki Shimizu
 * @since 3.4.0
 */
public enum AutoMappingUnknownColumnBehavior {

    /**
     * Do nothing (Default).
     * 不作任何处理
     */
    NONE {
        @Override
        public void doAction(MappedStatement mappedStatement, String columnName, String property, Class<?> propertyType) {
            // do nothing
        }
    },

    /**
     * Output warning log. Note: The log level of {@code 'org.apache.ibatis.session.AutoMappingUnknownColumnBehavior'}
     * must be set to {@code WARN}.
     * 输出告警日志
     */
    WARNING {
        @Override
        public void doAction(MappedStatement mappedStatement, String columnName, String property, Class<?> propertyType) {
            LogHolder.log.warn(buildMessage(mappedStatement, columnName, property, propertyType));
        }
    },

    /**
     * Fail mapping. Note: throw {@link SqlSessionException}.
     * 抛出异常
     */
    FAILING {
        @Override
        public void doAction(MappedStatement mappedStatement, String columnName, String property, Class<?> propertyType) {
            throw new SqlSessionException(buildMessage(mappedStatement, columnName, property, propertyType));
        }
    };

    /**
     * Perform the action when detects an unknown column (or unknown property type) of automatic mapping target.
     *
     * @param mappedStatement current mapped statement
     * @param columnName      column name for mapping target
     * @param propertyName    property name for mapping target
     * @param propertyType    property type for mapping target (If this argument is not null, {@link org.apache.ibatis.type.TypeHandler}
     *                        for property type is not registered)
     */
    public abstract void doAction(MappedStatement mappedStatement, String columnName, String propertyName,
                                  Class<?> propertyType);

    /**
     * 构建错误消息.
     * 当自动映射过程中检测到未知列时，此方法用于构建相应的错误信息.
     *
     * @param mappedStatement 映射语句对象，包含了SQL信息和映射参数等
     * @param columnName      数据库中的列名
     * @param property        映射到的对象属性名
     * @param propertyType    映射到的对象属性的类型
     * @return 返回构建的错误消息字符串，包含映射语句ID和映射参数信息
     */
    private static String buildMessage(MappedStatement mappedStatement, String columnName, String property,
                                       Class<?> propertyType) {
        // 使用StringBuilder拼接错误信息的各个部分
        return new StringBuilder("Unknown column is detected on '").append(mappedStatement.getId())
            .append("' auto-mapping. Mapping parameters are ").append("[").append("columnName=").append(columnName)
            .append(",").append("propertyName=").append(property).append(",").append("propertyType=")
            .append(propertyType != null ? propertyType.getName() : null).append("]").toString();
    }


    private static class LogHolder {
        private static final Log log = LogFactory.getLog(AutoMappingUnknownColumnBehavior.class);
    }

}
