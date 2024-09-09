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
package org.apache.ibatis.builder;

import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Clinton Begin
 */
public abstract class BaseBuilder {
    protected final Configuration configuration;
    protected final TypeAliasRegistry typeAliasRegistry;
    protected final TypeHandlerRegistry typeHandlerRegistry;

    public BaseBuilder(Configuration configuration) {
        this.configuration = configuration;
        this.typeAliasRegistry = this.configuration.getTypeAliasRegistry();
        this.typeHandlerRegistry = this.configuration.getTypeHandlerRegistry();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * 根据给定的正则表达式字符串编译出对应的Pattern对象
     * 如果给定的正则表达式字符串为null，则使用默认的正则表达式字符串进行编译
     *
     * @param regex        正则表达式字符串，可以是null
     * @param defaultValue 默认的正则表达式字符串，当regex为null时使用
     * @return 编译后的Pattern对象
     */
    protected Pattern parseExpression(String regex, String defaultValue) {
        return Pattern.compile(regex == null ? defaultValue : regex);
    }


    protected Boolean booleanValueOf(String value, Boolean defaultValue) {
        return value == null ? defaultValue : Boolean.valueOf(value);
    }

    /**
     * 根据给定的字符串值转换为Integer对象
     * 如果字符串为null，则返回默认值；如果字符串不为null，则尝试将其转换为Integer对象
     *
     * @param value        待转换的字符串值
     * @param defaultValue 如果字符串为null时的默认值
     * @return 转换后的Integer对象，或者在字符串为null时的默认值
     */
    protected Integer integerValueOf(String value, Integer defaultValue) {
        return value == null ? defaultValue : Integer.valueOf(value);
    }


    /**
     * 将给定的字符串值转换为字符串集合值
     * 如果输入的字符串为null，则使用默认值
     * 输入的字符串是逗号分隔的值，将其拆分为字符串数组，并转换为集合
     *
     * @param value        输入的字符串值，可能为null
     * @param defaultValue 默认值，当输入值为null时使用
     * @return 返回一个包含分割后的字符串的集合
     */
    protected Set<String> stringSetValueOf(String value, String defaultValue) {
        // 如果value为null，则使用defaultValue，否则使用value本身
        value = value == null ? defaultValue : value;
        // 将value按逗号分割为字符串数组，并转换为HashSet集合返回
        return new HashSet<>(Arrays.asList(value.split(",")));
    }


    /**
     * 根据给定的别名解析对应的JdbcType枚举值
     *
     * @param alias 别名，即JdbcType枚举值的字符串表示
     * @return 对应的JdbcType枚举值如果别名为null，则返回null
     * @throws BuilderException 如果别名无法解析为有效的JdbcType枚举值，则抛出此异常
     */
    protected JdbcType resolveJdbcType(String alias) {
        try {
            // 当别名为null时返回null，否则通过别名获取对应的JdbcType枚举值
            return alias == null ? null : JdbcType.valueOf(alias);
        } catch (IllegalArgumentException e) {
            // 如果无法通过别名获取JdbcType枚举值，抛出包含原始异常的BuilderException
            throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
        }
    }

    protected ResultSetType resolveResultSetType(String alias) {
        try {
            return alias == null ? null : ResultSetType.valueOf(alias);
        } catch (IllegalArgumentException e) {
            throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
        }
    }

    protected ParameterMode resolveParameterMode(String alias) {
        try {
            return alias == null ? null : ParameterMode.valueOf(alias);
        } catch (IllegalArgumentException e) {
            throw new BuilderException("Error resolving ParameterMode. Cause: " + e, e);
        }
    }

    /**
     * 根据别名创建实例
     * 该方法首先通过resolveClass方法解析别名为Class对象，然后尝试创建该类的实例
     * 如果解析出的Class对象为null，则返回null
     * 如果无法创建实例，将抛出包含错误原因的BuilderException异常
     *
     * @param alias 别名，用于标识要创建的类
     * @return 创建的实例，如果无法解析别名或创建实例失败，则可能返回null
     * @throws BuilderException 如果实例创建失败，将抛出此异常，包含错误原因
     */
    protected Object createInstance(String alias) {
        // 通过别名解析Class对象
        Class<?> clazz = resolveClass(alias);
        try {
            // 如果Class对象为null，则直接返回null，避免后续操作失败
            // 否则，获取Class对象的默认构造函数并创建新实例
            return clazz == null ? null : clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // 如果实例创建过程中发生异常，转换为BuilderException并包含原始异常信息抛出
            throw new BuilderException("Error creating instance. Cause: " + e, e);
        }
    }

    /**
     * 通过别名解析类
     *
     * @param <T>   泛型参数，用于指定返回的类的上界
     * @param alias 类的别名
     * @return 解析到的类的类型，是传入泛型参数的子类或自身
     * @throws BuilderException 如果解析过程中出现错误，则抛出此自定义异常
     */
    protected <T> Class<? extends T> resolveClass(String alias) {
        // 当别名为空时，直接返回null
        try {
            return alias == null ? null : resolveAlias(alias);
        } catch (Exception e) {
            // 捕获解析过程中可能出现的异常，并抛出自定义异常
            throw new BuilderException("Error resolving class. Cause: " + e, e);
        }
    }


    /**
     * 根据java类型和类型处理器别名解析出类型处理器
     *
     * @param javaType         java类型，用于确定需要处理的具体类型
     * @param typeHandlerAlias 类型处理器别名，用于根据别名查找对应的类型处理器类
     * @return 返回解析出的类型处理器实例如果给定的别名为空或者无法解析为类型处理器类，则返回null
     * @throws BuilderException 如果解析出的类不是类型处理器（不实现TypeHandler接口），则抛出此异常
     */
    protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
        // 如果类型处理器别名为空，则直接返回null
        if (typeHandlerAlias == null) {
            return null;
        }
        // 通过别名解析类
        Class<?> type = resolveClass(typeHandlerAlias);
        // 如果解析出的类不为空且不是TypeHandler的实现类，则抛出异常
        if (type != null && !TypeHandler.class.isAssignableFrom(type)) {
            throw new BuilderException(
                "Type " + type.getName() + " is not a valid TypeHandler because it does not implement TypeHandler interface");
        }
        // 将解析出的类强制转换为TypeHandler的子类（已经验证它是TypeHandler）
        @SuppressWarnings("unchecked") // already verified it is a TypeHandler
        Class<? extends TypeHandler<?>> typeHandlerType = (Class<? extends TypeHandler<?>>) type;
        // 使用java类型和类型处理器类再次解析类型处理器
        return resolveTypeHandler(javaType, typeHandlerType);
    }


    protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
        if (typeHandlerType == null) {
            return null;
        }
        // javaType ignored for injected handlers see issue #746 for full detail
        TypeHandler<?> handler = typeHandlerRegistry.getMappingTypeHandler(typeHandlerType);
        // if handler not in registry, create a new one, otherwise return directly
        return handler == null ? typeHandlerRegistry.getInstance(javaType, typeHandlerType) : handler;
    }

    /**
     * 通过别名解析实际类型
     *
     * @param <T>   泛型参数，使得方法能够返回任意类型的类
     * @param alias 别名，用于标识某个特定的类型
     * @return 解析后的实际类型，继承自泛型参数T
     * <p>
     * 该方法使用TypeAliasRegistry实例来解析给定的别名，
     * 返回一个与别名对应的实际类型类。泛型确保了返回的类
     * 是期望类型的子类或实现类。
     */
    protected <T> Class<? extends T> resolveAlias(String alias) {
        return typeAliasRegistry.resolveAlias(alias);
    }

}
