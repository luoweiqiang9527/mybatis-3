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

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 类别名注册器，含基本类型及其包装类型，基本类型数组和包装类型数组。
 * 主要有2类方法。注册类型别名(registerAlias)和根据类型别名获取到对应的类型(resolveAlias)。
 *
 * @author Clinton Begin
 */
public class TypeAliasRegistry {

    private final Map<String, Class<?>> typeAliases = new HashMap<>();

    //无参构造器，默认注册一些常用的类型别名。
    public TypeAliasRegistry() {
        registerAlias("string", String.class);

        registerAlias("byte", Byte.class);
        registerAlias("char", Character.class);
        registerAlias("character", Character.class);
        registerAlias("long", Long.class);
        registerAlias("short", Short.class);
        registerAlias("int", Integer.class);
        registerAlias("integer", Integer.class);
        registerAlias("double", Double.class);
        registerAlias("float", Float.class);
        registerAlias("boolean", Boolean.class);

        registerAlias("byte[]", Byte[].class);
        registerAlias("char[]", Character[].class);
        registerAlias("character[]", Character[].class);
        registerAlias("long[]", Long[].class);
        registerAlias("short[]", Short[].class);
        registerAlias("int[]", Integer[].class);
        registerAlias("integer[]", Integer[].class);
        registerAlias("double[]", Double[].class);
        registerAlias("float[]", Float[].class);
        registerAlias("boolean[]", Boolean[].class);

        registerAlias("_byte", byte.class);
        registerAlias("_char", char.class);
        registerAlias("_character", char.class);
        registerAlias("_long", long.class);
        registerAlias("_short", short.class);
        registerAlias("_int", int.class);
        registerAlias("_integer", int.class);
        registerAlias("_double", double.class);
        registerAlias("_float", float.class);
        registerAlias("_boolean", boolean.class);

        registerAlias("_byte[]", byte[].class);
        registerAlias("_char[]", char[].class);
        registerAlias("_character[]", char[].class);
        registerAlias("_long[]", long[].class);
        registerAlias("_short[]", short[].class);
        registerAlias("_int[]", int[].class);
        registerAlias("_integer[]", int[].class);
        registerAlias("_double[]", double[].class);
        registerAlias("_float[]", float[].class);
        registerAlias("_boolean[]", boolean[].class);

        registerAlias("date", Date.class);
        registerAlias("decimal", BigDecimal.class);
        registerAlias("bigdecimal", BigDecimal.class);
        registerAlias("biginteger", BigInteger.class);
        registerAlias("object", Object.class);

        registerAlias("date[]", Date[].class);
        registerAlias("decimal[]", BigDecimal[].class);
        registerAlias("bigdecimal[]", BigDecimal[].class);
        registerAlias("biginteger[]", BigInteger[].class);
        registerAlias("object[]", Object[].class);

        registerAlias("map", Map.class);
        registerAlias("hashmap", HashMap.class);
        registerAlias("list", List.class);
        registerAlias("arraylist", ArrayList.class);
        registerAlias("collection", Collection.class);
        registerAlias("iterator", Iterator.class);

        registerAlias("ResultSet", ResultSet.class);
    }

    @SuppressWarnings("unchecked")
    // throws class cast exception as well if types cannot be assigned
    /**
     * 解析类型别名字符串到对应的Class类型。
     *
     * @param string 表示类型别名的字符串。如果为null，则返回null。
     * @return 解析后的Class类型。如果给定的字符串无法解析，则抛出TypeException异常。
     * @param <T> 解析后Class类型的目标类型。
     * @throws TypeException 如果给定的类型别名无法找到对应的Class类型，则抛出此异常。
     */
    public <T> Class<T> resolveAlias(String string) {
        try {
            if (string == null) {
                return null;
            }
            // 将输入字符串转换为小写，以支持不区分大小写的类型别名查找
            String key = string.toLowerCase(Locale.ENGLISH);
            Class<T> value;
            // 检查类型别名是否存在，如果存在则使用别名对应的Class类型，否则尝试加载指定名称的Class
            if (typeAliases.containsKey(key)) {
                value = (Class<T>) typeAliases.get(key);
            } else {
                value = (Class<T>) Resources.classForName(string);
            }
            return value;
        } catch (ClassNotFoundException e) {
            // 当类无法找到时，抛出类型异常
            throw new TypeException("Could not resolve type alias '" + string + "'.  Cause: " + e, e);
        }
    }

    /**
     * 根据指定的包名注册别名
     * 该方法重载了registerAliases方法，当未指定别名类型时，默认将包名下的所有类注册为Object类型
     *
     * @param packageName 需要注册别名的包名
     */
    public void registerAliases(String packageName) {
        registerAliases(packageName, Object.class);
    }

    /**
     * 在给定的包名下注册所有具有指定超类型的类的别名
     * 这个方法用于发现所有符合特定条件的类，并为它们注册别名
     *
     * @param packageName 要扫描的包名
     * @param superType   指定的超类型，用于筛选符合条件的类
     */
    public void registerAliases(String packageName, Class<?> superType) {
        // 使用ResolverUtil工具来查找符合条件的类
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
        // 查找所有扩展了指定超类型的类
        resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
        // 获取所有找到的类集合
        Set<Class<? extends Class<?>>> typeSet = resolverUtil.getClasses();

        // 遍历所有找到的类
        for (Class<?> type : typeSet) {
            // 忽略匿名内部类、接口和成员内部类，只处理符合条件的顶级类
            if (!type.isAnonymousClass() && !type.isInterface() && !type.isMemberClass()) {
                // 为符合条件的类注册别名
                registerAlias(type);
            }
        }
    }


    /**
     * 注册类的别名
     * 该方法用于在系统中注册一个类的别名，方便后续通过别名引用该类
     * 它首先尝试使用类上的Alias注解定义的别名，如果未定义，则使用类的简单名称作为别名
     *
     * @param type 要注册的类的类型对象 该类型对象用于获取类的名称或其上定义的Alias注解
     */
    public void registerAlias(Class<?> type) {
        // 默认别名初始化为类的简单名称
        String alias = type.getSimpleName();

        // 检查类上是否定义了Alias注解
        Alias aliasAnnotation = type.getAnnotation(Alias.class);
        if (aliasAnnotation != null) {
            // 如果定义了Alias注解，则使用注解中指定的别名值
            alias = aliasAnnotation.value();
        }

        // 调用重载的registerAlias方法，完成别名与类型的注册
        registerAlias(alias, type);
    }

    /**
     * 注册一个别名到对应的类。
     *
     * @param alias 别名，不能为null。此参数将被转换为小写形式存储。
     * @param value 类型，对应别名的类。不能为null。
     * @throws TypeException 如果别名已经存在且映射到了不同的类，抛出此异常。
     */
    public void registerAlias(String alias, Class<?> value) {
        if (alias == null) {
            throw new TypeException("The parameter alias cannot be null");
        }
        // 将别名转换为小写，以支持大小写不敏感的查找。
        String key = alias.toLowerCase(Locale.ENGLISH);
        // 检查别名是否已经被注册且映射到了不同的类。
        if (typeAliases.containsKey(key) && typeAliases.get(key) != null && !typeAliases.get(key).equals(value)) {
            throw new TypeException(
                "The alias '" + alias + "' is already mapped to the value '" + typeAliases.get(key).getName() + "'.");
        }
        // 注册别名和对应的类。
        typeAliases.put(key, value);
    }


    /**
     * 注册类型别名。
     * <p>
     * 此方法将字符串注册为类类型的别名，允许在后续查找类时使用该别名。
     * 这主要简化了配置过程，提高了代码的可读性和可维护性。
     * </p>
     *
     * @param alias 要注册的别名，作为类类型的简化表示。
     * @param value 别名所代表的完全限定类名。
     * @throws TypeException 如果找不到指定的完全限定类名对应的类，则抛出包含 ClassNotFoundException 的 TypeException。
     */
    public void registerAlias(String alias, String value) {
        try {
            // 尝试通过将完全限定类名转换为类类型来注册别名
            registerAlias(alias, Resources.classForName(value));
        } catch (ClassNotFoundException e) {
            // 如果找不到类，则抛出包含 ClassNotFoundException 的 TypeException
            throw new TypeException("注册类型别名 " + alias + " 对应 " + value + " 时出错。原因: " + e, e);
        }
    }


    /**
     * Gets the type aliases.
     * 获取所有注册的类型别名。
     *
     * @return the type aliases
     * @since 3.2.2
     */
    public Map<String, Class<?>> getTypeAliases() {
        return Collections.unmodifiableMap(typeAliases);
    }

}
