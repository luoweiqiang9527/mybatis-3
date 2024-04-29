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
package org.apache.ibatis.mapping;

import java.sql.ResultSet;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public class ParameterMapping {

    private Configuration configuration;

    private String property;
    private ParameterMode mode;
    private Class<?> javaType = Object.class;
    private JdbcType jdbcType;
    private Integer numericScale;
    private TypeHandler<?> typeHandler;
    private String resultMapId;
    private String jdbcTypeName;
    private String expression;

    private ParameterMapping() {
    }

    /**
     * 构建者模式，用于构建ParameterMapping
     */
    public static class Builder {
        private final ParameterMapping parameterMapping = new ParameterMapping();

        public Builder(Configuration configuration, String property, TypeHandler<?> typeHandler) {
            parameterMapping.configuration = configuration;
            parameterMapping.property = property;
            parameterMapping.typeHandler = typeHandler;
            parameterMapping.mode = ParameterMode.IN;
        }

        public Builder(Configuration configuration, String property, Class<?> javaType) {
            parameterMapping.configuration = configuration;
            parameterMapping.property = property;
            parameterMapping.javaType = javaType;
            parameterMapping.mode = ParameterMode.IN;
        }

        public Builder mode(ParameterMode mode) {
            parameterMapping.mode = mode;
            return this;
        }

        public Builder javaType(Class<?> javaType) {
            parameterMapping.javaType = javaType;
            return this;
        }

        public Builder jdbcType(JdbcType jdbcType) {
            parameterMapping.jdbcType = jdbcType;
            return this;
        }

        public Builder numericScale(Integer numericScale) {
            parameterMapping.numericScale = numericScale;
            return this;
        }

        public Builder resultMapId(String resultMapId) {
            parameterMapping.resultMapId = resultMapId;
            return this;
        }

        public Builder typeHandler(TypeHandler<?> typeHandler) {
            parameterMapping.typeHandler = typeHandler;
            return this;
        }

        public Builder jdbcTypeName(String jdbcTypeName) {
            parameterMapping.jdbcTypeName = jdbcTypeName;
            return this;
        }

        public Builder expression(String expression) {
            parameterMapping.expression = expression;
            return this;
        }

        public ParameterMapping build() {
            resolveTypeHandler();
            validate();
            return parameterMapping;
        }

        /**
         * 校验参数映射是否有效。
         * 该方法不接受任何参数，也不返回任何结果。
         * 主要校验逻辑包括：
         * 1. 如果参数映射的javaType为ResultSet类，则必须存在对应的resultMapId；
         * 2. 如果参数映射的javaType不是ResultSet类，但typeHandler为空，则抛出异常。
         * 抛出的异常为IllegalStateException，包含具体的错误信息。
         */
        private void validate() {
            // 当参数映射的javaType为ResultSet时的校验逻辑
            if (ResultSet.class.equals(parameterMapping.javaType)) {
                // 如果resultMapId为空，则抛出异常
                if (parameterMapping.resultMapId == null) {
                    throw new IllegalStateException("Missing resultmap in property '" + parameterMapping.property + "'. "
                        + "Parameters of type java.sql.ResultSet require a resultmap.");
                }
            } else { // 当参数映射的javaType不是ResultSet时的校验逻辑
                // 如果typeHandler为空，则抛出异常
                if (parameterMapping.typeHandler == null) {
                    throw new IllegalStateException("Type handler was null on parameter mapping for property '"
                        + parameterMapping.property + "'. It was either not specified and/or could not be found for the javaType ("
                        + parameterMapping.javaType.getName() + ") : jdbcType (" + parameterMapping.jdbcType + ") combination.");
                }
            }
        }


        /**
         * 解析类型处理器。该方法用于根据参数映射的javaType来查找并设置相应的类型处理器。
         * 如果参数映射中未指定类型处理器（typeHandler为null），但指定了Java类型（javaType不为null），
         * 则会尝试从配置中获取对应的类型处理器。
         */
        private void resolveTypeHandler() {
            // 当参数映射的类型处理器为空且Java类型不为空时，尝试从配置中获取类型处理器
            if (parameterMapping.typeHandler == null && parameterMapping.javaType != null) {
                // 获取配置对象和类型处理器注册表
                Configuration configuration = parameterMapping.configuration;
                TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
                // 根据Java类型和JDBC类型获取对应的类型处理器，并设置到参数映射中
                parameterMapping.typeHandler = typeHandlerRegistry.getTypeHandler(parameterMapping.javaType,
                    parameterMapping.jdbcType);
            }
        }


    }

    public String getProperty() {
        return property;
    }

    /**
     * 用于处理可调用语句的输出。
     *
     * @return the mode
     */
    public ParameterMode getMode() {
        return mode;
    }

    /**
     * 用于处理可调用语句的输出。
     *
     * @return the java type
     */
    public Class<?> getJavaType() {
        return javaType;
    }

    /**
     * 在 UnknownTypeHandler 中用于，以防属性类型没有处理程序。
     *
     * @return the jdbc type
     */
    public JdbcType getJdbcType() {
        return jdbcType;
    }

    /**
     * 用于处理可调用语句的输出。
     *
     * @return the numeric scale
     */
    public Integer getNumericScale() {
        return numericScale;
    }

    /**
     * 在为 PreparedStatement 设置参数时使用。
     *
     * @return the type handler
     */
    public TypeHandler<?> getTypeHandler() {
        return typeHandler;
    }

    /**
     * 用于处理可调用语句的输出。
     *
     * @return the result map id
     */
    public String getResultMapId() {
        return resultMapId;
    }

    /**
     * 用于处理可调用语句的输出。
     *
     * @return the jdbc type name
     */
    public String getJdbcTypeName() {
        return jdbcTypeName;
    }

    /**
     * Expression 'Not used'.
     *
     * @return the expression
     */
    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ParameterMapping{");
        // sb.append("configuration=").append(configuration); // configuration doesn't have a useful .toString()
        sb.append("property='").append(property).append('\'');
        sb.append(", mode=").append(mode);
        sb.append(", javaType=").append(javaType);
        sb.append(", jdbcType=").append(jdbcType);
        sb.append(", numericScale=").append(numericScale);
        // sb.append(", typeHandler=").append(typeHandler); // typeHandler also doesn't have a useful .toString()
        sb.append(", resultMapId='").append(resultMapId).append('\'');
        sb.append(", jdbcTypeName='").append(jdbcTypeName).append('\'');
        sb.append(", expression='").append(expression).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
