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

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.chrono.JapaneseDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类型处理器注册表
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public final class TypeHandlerRegistry {
    // 存储JdbcType与TypeHandler的映射
    private final Map<JdbcType, TypeHandler<?>> jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);
    // 存储Type与JdbcType与TypeHandler的映射
    private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();
    // 未知类型处理器
    private final TypeHandler<Object> unknownTypeHandler;
    // 存储Class与TypeHandler的映射
    private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();
    // 空类型处理器映射
    private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();
    // 默认枚举类型处理器
    private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

    /**
     * 默认构造函数。
     */
    public TypeHandlerRegistry() {
        this(new Configuration());
    }

    /**
     * 传递MyBatis配置的构造函数。
     *
     * @param configuration a MyBatis configuration
     * @since 3.5.4
     */
    public TypeHandlerRegistry(Configuration configuration) {
        this.unknownTypeHandler = new UnknownTypeHandler(configuration);

        register(Boolean.class, new BooleanTypeHandler());
        register(boolean.class, new BooleanTypeHandler());
        register(JdbcType.BOOLEAN, new BooleanTypeHandler());
        register(JdbcType.BIT, new BooleanTypeHandler());

        register(Byte.class, new ByteTypeHandler());
        register(byte.class, new ByteTypeHandler());
        register(JdbcType.TINYINT, new ByteTypeHandler());

        register(Short.class, new ShortTypeHandler());
        register(short.class, new ShortTypeHandler());
        register(JdbcType.SMALLINT, new ShortTypeHandler());

        register(Integer.class, new IntegerTypeHandler());
        register(int.class, new IntegerTypeHandler());
        register(JdbcType.INTEGER, new IntegerTypeHandler());

        register(Long.class, new LongTypeHandler());
        register(long.class, new LongTypeHandler());

        register(Float.class, new FloatTypeHandler());
        register(float.class, new FloatTypeHandler());
        register(JdbcType.FLOAT, new FloatTypeHandler());

        register(Double.class, new DoubleTypeHandler());
        register(double.class, new DoubleTypeHandler());
        register(JdbcType.DOUBLE, new DoubleTypeHandler());

        register(Reader.class, new ClobReaderTypeHandler());
        register(String.class, new StringTypeHandler());
        register(String.class, JdbcType.CHAR, new StringTypeHandler());
        register(String.class, JdbcType.CLOB, new ClobTypeHandler());
        register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
        register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
        register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
        register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
        register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
        register(JdbcType.CHAR, new StringTypeHandler());
        register(JdbcType.VARCHAR, new StringTypeHandler());
        register(JdbcType.CLOB, new ClobTypeHandler());
        register(JdbcType.LONGVARCHAR, new StringTypeHandler());
        register(JdbcType.NVARCHAR, new NStringTypeHandler());
        register(JdbcType.NCHAR, new NStringTypeHandler());
        register(JdbcType.NCLOB, new NClobTypeHandler());

        register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
        register(JdbcType.ARRAY, new ArrayTypeHandler());

        register(BigInteger.class, new BigIntegerTypeHandler());
        register(JdbcType.BIGINT, new LongTypeHandler());

        register(BigDecimal.class, new BigDecimalTypeHandler());
        register(JdbcType.REAL, new BigDecimalTypeHandler());
        register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
        register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

        register(InputStream.class, new BlobInputStreamTypeHandler());
        register(Byte[].class, new ByteObjectArrayTypeHandler());
        register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
        register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
        register(byte[].class, new ByteArrayTypeHandler());
        register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
        register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
        register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
        register(JdbcType.BLOB, new BlobTypeHandler());

        register(Object.class, unknownTypeHandler);
        register(Object.class, JdbcType.OTHER, unknownTypeHandler);
        register(JdbcType.OTHER, unknownTypeHandler);

        register(Date.class, new DateTypeHandler());
        register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
        register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
        register(JdbcType.TIMESTAMP, new DateTypeHandler());
        register(JdbcType.DATE, new DateOnlyTypeHandler());
        register(JdbcType.TIME, new TimeOnlyTypeHandler());

        register(java.sql.Date.class, new SqlDateTypeHandler());
        register(java.sql.Time.class, new SqlTimeTypeHandler());
        register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

        register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

        register(Instant.class, new InstantTypeHandler());
        register(LocalDateTime.class, new LocalDateTimeTypeHandler());
        register(LocalDate.class, new LocalDateTypeHandler());
        register(LocalTime.class, new LocalTimeTypeHandler());
        register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
        register(OffsetTime.class, new OffsetTimeTypeHandler());
        register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
        register(Month.class, new MonthTypeHandler());
        register(Year.class, new YearTypeHandler());
        register(YearMonth.class, new YearMonthTypeHandler());
        register(JapaneseDate.class, new JapaneseDateTypeHandler());

        // issue #273
        register(Character.class, new CharacterTypeHandler());
        register(char.class, new CharacterTypeHandler());
    }

    /**
     * Set a default {@link TypeHandler} class for {@link Enum}. A default {@link TypeHandler} is
     * {@link org.apache.ibatis.type.EnumTypeHandler}.
     *
     * @param typeHandler a type handler class for {@link Enum}
     * @since 3.4.5
     */
    public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
        this.defaultEnumTypeHandler = typeHandler;
    }

    public boolean hasTypeHandler(Class<?> javaType) {
        return hasTypeHandler(javaType, null);
    }

    public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
        return hasTypeHandler(javaTypeReference, null);
    }

    public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
        return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
    }

    public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
        return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
    }

    public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
        return allTypeHandlersMap.get(handlerType);
    }

    public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
        return getTypeHandler((Type) type, null);
    }

    public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
        return getTypeHandler(javaTypeReference, null);
    }

    public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
        return jdbcTypeHandlerMap.get(jdbcType);
    }

    public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
        return getTypeHandler((Type) type, jdbcType);
    }

    public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
        return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
    }

    /**
     * 根据给定的类型和JDBC类型获取相应的类型处理器
     *
     * @param type Java类型，用于确定类型处理器
     * @param jdbcType JDBC类型，用于进一步确定类型处理器
     * @return 对应的类型处理器，如果找不到，则返回null
     */
    @SuppressWarnings("unchecked")
    private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
        // 如果是ParamMap类型，则不处理，直接返回null
        if (ParamMap.class.equals(type)) {
            return null;
        }
        // 获取与Java类型相关联的JDBC处理器映射
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
        TypeHandler<?> handler = null;
        if (jdbcHandlerMap != null) {
            // 尝试根据JDBC类型获取处理器
            handler = jdbcHandlerMap.get(jdbcType);
            if (handler == null) {
                // 如果未找到，尝试仅用null作为键获取处理器
                handler = jdbcHandlerMap.get(null);
            }
            if (handler == null) {
                // 如果仍然未找到，尝试从映射中挑选唯一的处理器
                handler = pickSoleHandler(jdbcHandlerMap);
            }
        }
        // 强制类型转换，确保返回的处理器与泛型参数T兼容
        return (TypeHandler<T>) handler;
    }


    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
        if (jdbcHandlerMap != null) {
            return NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap) ? null : jdbcHandlerMap;
        }
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (Enum.class.isAssignableFrom(clazz)) {
                if (clazz.isAnonymousClass()) {
                    return getJdbcHandlerMap(clazz.getSuperclass());
                }
                jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(clazz, clazz);
                if (jdbcHandlerMap == null) {
                    register(clazz, getInstance(clazz, defaultEnumTypeHandler));
                    return typeHandlerMap.get(clazz);
                }
            } else {
                jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
            }
        }
        typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
        return jdbcHandlerMap;
    }

    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
        for (Class<?> iface : clazz.getInterfaces()) {
            Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
            if (jdbcHandlerMap == null) {
                jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
            }
            if (jdbcHandlerMap != null) {
                // Found a type handler registered to a super interface
                HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
                for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
                    // Create a type handler instance with enum type as a constructor arg
                    newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
                }
                return newMap;
            }
        }
        return null;
    }

    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null || Object.class.equals(superclass)) {
            return null;
        }
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
        if (jdbcHandlerMap != null) {
            return jdbcHandlerMap;
        }
        return getJdbcHandlerMapForSuperclass(superclass);
    }

    /**
     * 从给定的类型处理器映射中挑选出唯一的类型处理器如果映射中只有一个类型的处理器，则返回该处理器；否则返回null
     *
     * @param jdbcHandlerMap Jdbc类型到类型处理器的映射
     * @return 唯一的类型处理器，如果存在；否则返回null
     */
    private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
        // 初始化唯一的处理器变量为null
        TypeHandler<?> soleHandler = null;
        // 遍历jdbcHandlerMap中的每个类型处理器
        for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
            // 如果soleHandler为空，则将其初始化为当前处理器
            if (soleHandler == null) {
                soleHandler = handler;
            } else if (!handler.getClass().equals(soleHandler.getClass())) {
                // 如果遍历到的处理器与soleHandler类型不同，则说明存在多个类型的处理器，返回null
                return null;
            }
        }
        // 如果遍历完成没有返回null，则说明只有一个类型的处理器，返回该处理器
        return soleHandler;
    }


    public TypeHandler<Object> getUnknownTypeHandler() {
        return unknownTypeHandler;
    }

    public void register(JdbcType jdbcType, TypeHandler<?> handler) {
        jdbcTypeHandlerMap.put(jdbcType, handler);
    }

    //
    // REGISTER INSTANCE
    //

    // Only handler

    @SuppressWarnings("unchecked")
    public <T> void register(TypeHandler<T> typeHandler) {
        boolean mappedTypeFound = false;
        MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
        if (mappedTypes != null) {
            for (Class<?> handledType : mappedTypes.value()) {
                register(handledType, typeHandler);
                mappedTypeFound = true;
            }
        }
        // @since 3.1.0 - try to auto-discover the mapped type
        if (!mappedTypeFound && typeHandler instanceof TypeReference) {
            try {
                TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
                register(typeReference.getRawType(), typeHandler);
                mappedTypeFound = true;
            } catch (Throwable t) {
                // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
            }
        }
        if (!mappedTypeFound) {
            register((Class<T>) null, typeHandler);
        }
    }

    // java type + handler

    /**
     * 注册一个类型处理器，用于处理特定Java类型的参数。
     * 该方法提供了一种更方便的注册方式，直接使用Java类型而不是Type对象。
     *
     * @param javaType    要注册的Java类型，如String.class。
     * @param typeHandler 对应类型处理器，用于处理该类型的数据。
     */
    public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
        register((Type) javaType, typeHandler);
    }


    /**
     * 注册类型处理器到MyBatis框架中
     * 该方法支持基于Java类型和JDBC类型的映射关系来注册类型处理器
     * 如果提供了MappedJdbcTypes注解，则会根据注解内容注册多个JDBC类型对应的处理器；
     * 否则，默认注册为null的JDBC类型处理器
     *
     * @param javaType    Java类型，与之关联的JDBC类型
     * @param typeHandler 类型处理器，用于处理Java类型和JDBC类型之间的转换
     */
    private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
        // 获取类型处理器上可能标注的MappedJdbcTypes注解信息
        MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
        if (mappedJdbcTypes != null) {
            // 如果存在MappedJdbcTypes注解，则遍历注解中定义的JDBC类型，并注册对应的处理器
            for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
                register(javaType, handledJdbcType, typeHandler);
            }
            // 检查是否需要为null的JDBC类型注册处理器
            if (mappedJdbcTypes.includeNullJdbcType()) {
                register(javaType, null, typeHandler);
            }
        } else {
            // 如果没有MappedJdbcTypes注解，默认为null的JDBC类型注册处理器
            register(javaType, null, typeHandler);
        }
    }


    public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
        register(javaTypeReference.getRawType(), handler);
    }

    // java type + jdbc type + handler

    // Cast is required here
    @SuppressWarnings("cast")
    public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
        register((Type) type, jdbcType, handler);
    }

    /**
     * 注册类型处理器到相应的Java类型和JDBC类型映射中
     *
     * @param javaType    需要映射的Java类型
     * @param jdbcType    需要映射的JDBC类型
     * @param handler     类型处理器，用于在Java类型和JDBC类型间进行转换
     */
    private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
        // 检查Java类型是否为空，为空则无需注册
        if (javaType != null) {
            // 获取或初始化存储JDBC类型到类型处理器映射的Map
            Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);
            if (map == null || map == NULL_TYPE_HANDLER_MAP) {
                map = new HashMap<>();
            }
            // 将JDBC类型和类型处理器映射添加到Map中
            map.put(jdbcType, handler);
            // 更新typeHandlerMap，建立Java类型和处理后的映射关系
            typeHandlerMap.put(javaType, map);
        }
        // 将类型处理器映射到其对应的类上，确保每个类型的处理器唯一
        allTypeHandlersMap.put(handler.getClass(), handler);
    }


    //
    // REGISTER CLASS
    //

    // Only handler type

    /**
     * 注册类型处理器。如果提供了MappedTypes注解，则会根据注解中的类型进行注册；
     * 如果没有提供，则注册默认的类型处理器实例。
     *
     * @param typeHandlerClass 类型处理器类，用于注册到类型处理器映射中。
     */
    public void register(Class<?> typeHandlerClass) {
        // 标记是否找到了映射的类型，初始为未找到
        boolean mappedTypeFound = false;

        // 获取类型处理器类上的MappedTypes注解
        MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);

        // 如果找到了MappedTypes注解
        if (mappedTypes != null) {
            // 遍历MappedTypes注解中的所有Java类型
            for (Class<?> javaTypeClass : mappedTypes.value()) {
                // 注册当前Java类型与类型处理器类的映射
                register(javaTypeClass, typeHandlerClass);
                // 标记为已找到映射的类型
                mappedTypeFound = true;
            }
        }

        // 如果没有找到映射的类型，则注册默认的类型处理器实例
        if (!mappedTypeFound) {
            register(getInstance(null, typeHandlerClass));
        }
    }


    // java type + handler type

    public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
        register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
    }

    public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
        register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
    }

    // java type + jdbc type + handler type

    public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
        register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
    }

    // Construct a handler (used also from Builders)

    @SuppressWarnings("unchecked")
    public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
        if (javaTypeClass != null) {
            try {
                Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
                return (TypeHandler<T>) c.newInstance(javaTypeClass);
            } catch (NoSuchMethodException ignored) {
                // ignored
            } catch (Exception e) {
                throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
            }
        }
        try {
            Constructor<?> c = typeHandlerClass.getConstructor();
            return (TypeHandler<T>) c.newInstance();
        } catch (Exception e) {
            throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
        }
    }

    // scan

    /**
 * 注册指定包名下的所有类型处理器。
 *
 * 该方法使用 ResolverUtil 扫描指定包名下的所有类，过滤掉内部类、接口和抽象类，
 * 并将剩余的类注册为类型处理器。
 *
 * @param packageName 要扫描类型处理器的包名。
 */
public void register(String packageName) {
    // 创建 ResolverUtil 实例用于类解析
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    // 使用 ResolverUtil 查找指定包下所有实现了 TypeHandler 接口的类
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
    // 获取实现 TypeHandler 接口的类集合
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
    // 遍历类型处理器集合
    for (Class<?> type : handlerSet) {
        // 忽略内部类、接口和抽象类
        if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
            // 将符合条件的类注册为类型处理器
            register(type);
        }
    }
}


    // get information

    /**
     * Gets the type handlers.
     *
     * @return the type handlers
     * @since 3.2.2
     */
    public Collection<TypeHandler<?>> getTypeHandlers() {
        return Collections.unmodifiableCollection(allTypeHandlersMap.values());
    }

}
