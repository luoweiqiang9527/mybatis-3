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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.CacheBuilder;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
public class MapperBuilderAssistant extends BaseBuilder {

    private String currentNamespace;
    private final String resource;
    private Cache currentCache;
    private boolean unresolvedCacheRef; // issue #676

    public MapperBuilderAssistant(Configuration configuration, String resource) {
        super(configuration);
        ErrorContext.instance().resource(resource);
        this.resource = resource;
    }

    public String getCurrentNamespace() {
        return currentNamespace;
    }

    public void setCurrentNamespace(String currentNamespace) {
        if (currentNamespace == null) {
            throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
        }

        if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
            throw new BuilderException(
                "Wrong namespace. Expected '" + this.currentNamespace + "' but found '" + currentNamespace + "'.");
        }

        this.currentNamespace = currentNamespace;
    }

    public String applyCurrentNamespace(String base, boolean isReference) {
        if (base == null) {
            return null;
        }
        if (isReference) {
            // is it qualified with any namespace yet?
            if (base.contains(".")) {
                return base;
            }
        } else {
            // is it qualified with this namespace yet?
            if (base.startsWith(currentNamespace + ".")) {
                return base;
            }
            if (base.contains(".")) {
                throw new BuilderException("Dots are not allowed in element names, please remove it from " + base);
            }
        }
        return currentNamespace + "." + base;
    }

    public Cache useCacheRef(String namespace) {
        if (namespace == null) {
            throw new BuilderException("cache-ref element requires a namespace attribute.");
        }
        try {
            unresolvedCacheRef = true;
            Cache cache = configuration.getCache(namespace);
            if (cache == null) {
                throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.");
            }
            currentCache = cache;
            unresolvedCacheRef = false;
            return cache;
        } catch (IllegalArgumentException e) {
            throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
        }
    }

    /**
     * 使用新的缓存策略替换现有缓存
     *
     * @param typeClass 缓存实现的类型类，如果未指定，则默认为 PerpetualCache
     * @param evictionClass 缓存淘汰策略的类型类，如果未指定，则默认为 LruCache
     * @param flushInterval 缓存清理的间隔时间，单位为毫秒如果为 null，则表示永不清理
     * @param size 缓存的最大大小如果为 null，则使用默认大小
     * @param readWrite 指定缓存是否支持读写如果为 true，则创建读写缓存；否则创建只读缓存
     * @param blocking 指定在缓存写操作时是否阻塞如果为 true，则在缓存满时写操作会阻塞；否则写操作会覆盖旧值
     * @param props 额外的缓存配置属性
     * @return 返回新创建的缓存实例
     */
    public Cache useNewCache(Class<? extends Cache> typeClass, Class<? extends Cache> evictionClass, Long flushInterval,
                             Integer size, boolean readWrite, boolean blocking, Properties props) {
        // 创建新的缓存实例，配置命名空间、缓存类型、缓存装饰器、清理间隔、大小、读写模式、阻塞模式以及额外属性
        Cache cache = new CacheBuilder(currentNamespace).implementation(valueOrDefault(typeClass, PerpetualCache.class))
            .addDecorator(valueOrDefault(evictionClass, LruCache.class)).clearInterval(flushInterval).size(size)
            .readWrite(readWrite).blocking(blocking).properties(props).build();

        // 将新缓存添加到配置中，并设置为当前使用的缓存
        configuration.addCache(cache);
        currentCache = cache;

        // 返回新创建的缓存实例
        return cache;
    }

    public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
        id = applyCurrentNamespace(id, false);
        ParameterMap parameterMap = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings).build();
        configuration.addParameterMap(parameterMap);
        return parameterMap;
    }

    public ParameterMapping buildParameterMapping(Class<?> parameterType, String property, Class<?> javaType,
                                                  JdbcType jdbcType, String resultMap, ParameterMode parameterMode, Class<? extends TypeHandler<?>> typeHandler,
                                                  Integer numericScale) {
        resultMap = applyCurrentNamespace(resultMap, true);

        // Class parameterType = parameterMapBuilder.type();
        Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
        TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

        return new ParameterMapping.Builder(configuration, property, javaTypeClass).jdbcType(jdbcType)
            .resultMapId(resultMap).mode(parameterMode).numericScale(numericScale).typeHandler(typeHandlerInstance).build();
    }

    public ResultMap addResultMap(String id, Class<?> type, String extend, Discriminator discriminator,
                                  List<ResultMapping> resultMappings, Boolean autoMapping) {
        id = applyCurrentNamespace(id, false);
        extend = applyCurrentNamespace(extend, true);

        if (extend != null) {
            if (!configuration.hasResultMap(extend)) {
                throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
            }
            ResultMap resultMap = configuration.getResultMap(extend);
            List<ResultMapping> extendedResultMappings = new ArrayList<>(resultMap.getResultMappings());
            extendedResultMappings.removeAll(resultMappings);
            // Remove parent constructor if this resultMap declares a constructor.
            boolean declaresConstructor = false;
            for (ResultMapping resultMapping : resultMappings) {
                if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
                    declaresConstructor = true;
                    break;
                }
            }
            if (declaresConstructor) {
                extendedResultMappings.removeIf(resultMapping -> resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR));
            }
            resultMappings.addAll(extendedResultMappings);
        }
        ResultMap resultMap = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping)
            .discriminator(discriminator).build();
        configuration.addResultMap(resultMap);
        return resultMap;
    }

    public Discriminator buildDiscriminator(Class<?> resultType, String column, Class<?> javaType, JdbcType jdbcType,
                                            Class<? extends TypeHandler<?>> typeHandler, Map<String, String> discriminatorMap) {
        ResultMapping resultMapping = buildResultMapping(resultType, null, column, javaType, jdbcType, null, null, null,
            null, typeHandler, new ArrayList<>(), null, null, false);
        Map<String, String> namespaceDiscriminatorMap = new HashMap<>();
        for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
            String resultMap = e.getValue();
            resultMap = applyCurrentNamespace(resultMap, true);
            namespaceDiscriminatorMap.put(e.getKey(), resultMap);
        }
        return new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap).build();
    }

    /**
     * 添加一个MappedStatement对象到配置中。
     * MappedStatement用于封装一个SQL映射语句的所有配置信息，包括SQL源、参数映射、结果映射等。
     * 此方法用于构建一个新的MappedStatement对象并将其添加到配置中，以便在执行SQL时使用。
     *
     * @param id 映射语句的唯一标识符，用于在配置中唯一标识这个映射语句。
     * @param sqlSource SQL源，用于提供动态或静态的SQL语句。
     * @param statementType 映射语句的类型，比如SELECT、INSERT、UPDATE或DELETE。
     * @param sqlCommandType SQL命令的类型，具体到MyBatis的SQL命令，比如SELECT_LIST、INSERT_IGNORE等。
     * @param fetchSize 用于指定结果集的默认获取大小。
     * @param timeout 设置这个映射语句的超时时间。
     * @param parameterMap 参数映射的标识符，用于指定如何映射输入参数。
     * @param parameterType 输入参数的类型。
     * @param resultMap 结果映射的标识符，用于指定如何映射查询结果。
     * @param resultType 查询结果的类型。
     * @param resultSetType 结果集的类型，比如FORWARD_ONLY、SCROLL_SENSITIVE等。
     * @param flushCache 是否在执行这个映射语句时刷新缓存。
     * @param useCache 是否使用缓存来存储这个映射语句的结果。
     * @param resultOrdered 是否要求结果集按照它们被返回的顺序进行处理。
     * @param keyGenerator 键值生成器，用于生成主键或唯一键。
     * @param keyProperty 指定生成的键值应该被设置到哪个属性上。
     * @param keyColumn 用于生成键值的数据库列。
     * @param databaseId 特定数据库的标识符，用于数据库特定的映射。
     * @param lang 用于语言特定的逻辑，比如条件表达式的解析。
     * @param resultSets 映射多个结果集时使用的标识符。
     * @param dirtySelect 标记是否为"脏"选择，影响缓存行为。
     * @return 返回构建完成的MappedStatement对象。
     * @throws IncompleteElementException 如果缓存引用未解析完成，则抛出此异常。
     */
    public MappedStatement addMappedStatement(String id, SqlSource sqlSource, StatementType statementType,
                                              SqlCommandType sqlCommandType, Integer fetchSize, Integer timeout, String parameterMap, Class<?> parameterType,
                                              String resultMap, Class<?> resultType, ResultSetType resultSetType, boolean flushCache, boolean useCache,
                                              boolean resultOrdered, KeyGenerator keyGenerator, String keyProperty, String keyColumn, String databaseId,
                                              LanguageDriver lang, String resultSets, boolean dirtySelect) {

        // 检查是否有未解析的缓存引用，如果有则抛出异常
        if (unresolvedCacheRef) {
            throw new IncompleteElementException("Cache-ref not yet resolved");
        }

        // 应用当前命名空间到id上，确保id的全局唯一性
        id = applyCurrentNamespace(id, false);

        // 使用Builder模式构建MappedStatement对象
        MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType)
            .resource(resource).fetchSize(fetchSize).timeout(timeout).statementType(statementType)
            .keyGenerator(keyGenerator).keyProperty(keyProperty).keyColumn(keyColumn).databaseId(databaseId).lang(lang)
            .resultOrdered(resultOrdered).resultSets(resultSets)
            .resultMaps(getStatementResultMaps(resultMap, resultType, id)).resultSetType(resultSetType)
            .flushCacheRequired(flushCache).useCache(useCache).cache(currentCache).dirtySelect(dirtySelect);

        // 根据参数映射标识符构建ParameterMap对象，并将其应用到MappedStatement上
        ParameterMap statementParameterMap = getStatementParameterMap(parameterMap, parameterType, id);
        if (statementParameterMap != null) {
            statementBuilder.parameterMap(statementParameterMap);
        }

        // 构建MappedStatement对象
        MappedStatement statement = statementBuilder.build();
        // 将MappedStatement添加到配置中
        configuration.addMappedStatement(statement);
        // 返回构建完成的MappedStatement对象
        return statement;
    }


    /**
     * Backward compatibility signature 'addMappedStatement'.
     *
     * @param id             the id
     * @param sqlSource      the sql source
     * @param statementType  the statement type
     * @param sqlCommandType the sql command type
     * @param fetchSize      the fetch size
     * @param timeout        the timeout
     * @param parameterMap   the parameter map
     * @param parameterType  the parameter type
     * @param resultMap      the result map
     * @param resultType     the result type
     * @param resultSetType  the result set type
     * @param flushCache     the flush cache
     * @param useCache       the use cache
     * @param resultOrdered  the result ordered
     * @param keyGenerator   the key generator
     * @param keyProperty    the key property
     * @param keyColumn      the key column
     * @param databaseId     the database id
     * @param lang           the lang
     * @return the mapped statement
     */
    public MappedStatement addMappedStatement(String id, SqlSource sqlSource, StatementType statementType,
                                              SqlCommandType sqlCommandType, Integer fetchSize, Integer timeout, String parameterMap, Class<?> parameterType,
                                              String resultMap, Class<?> resultType, ResultSetType resultSetType, boolean flushCache, boolean useCache,
                                              boolean resultOrdered, KeyGenerator keyGenerator, String keyProperty, String keyColumn, String databaseId,
                                              LanguageDriver lang, String resultSets) {
        return addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap,
            parameterType, resultMap, resultType, resultSetType, flushCache, useCache, resultOrdered, keyGenerator,
            keyProperty, keyColumn, databaseId, lang, null, false);
    }

    public MappedStatement addMappedStatement(String id, SqlSource sqlSource, StatementType statementType,
                                              SqlCommandType sqlCommandType, Integer fetchSize, Integer timeout, String parameterMap, Class<?> parameterType,
                                              String resultMap, Class<?> resultType, ResultSetType resultSetType, boolean flushCache, boolean useCache,
                                              boolean resultOrdered, KeyGenerator keyGenerator, String keyProperty, String keyColumn, String databaseId,
                                              LanguageDriver lang) {
        return addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap,
            parameterType, resultMap, resultType, resultSetType, flushCache, useCache, resultOrdered, keyGenerator,
            keyProperty, keyColumn, databaseId, lang, null);
    }

    private <T> T valueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    private ParameterMap getStatementParameterMap(String parameterMapName, Class<?> parameterTypeClass,
                                                  String statementId) {
        parameterMapName = applyCurrentNamespace(parameterMapName, true);
        ParameterMap parameterMap = null;
        if (parameterMapName != null) {
            try {
                parameterMap = configuration.getParameterMap(parameterMapName);
            } catch (IllegalArgumentException e) {
                throw new IncompleteElementException("Could not find parameter map " + parameterMapName, e);
            }
        } else if (parameterTypeClass != null) {
            List<ParameterMapping> parameterMappings = new ArrayList<>();
            parameterMap = new ParameterMap.Builder(configuration, statementId + "-Inline", parameterTypeClass,
                parameterMappings).build();
        }
        return parameterMap;
    }

    private List<ResultMap> getStatementResultMaps(String resultMap, Class<?> resultType, String statementId) {
        resultMap = applyCurrentNamespace(resultMap, true);

        List<ResultMap> resultMaps = new ArrayList<>();
        if (resultMap != null) {
            String[] resultMapNames = resultMap.split(",");
            for (String resultMapName : resultMapNames) {
                try {
                    resultMaps.add(configuration.getResultMap(resultMapName.trim()));
                } catch (IllegalArgumentException e) {
                    throw new IncompleteElementException(
                        "Could not find result map '" + resultMapName + "' referenced from '" + statementId + "'", e);
                }
            }
        } else if (resultType != null) {
            ResultMap inlineResultMap = new ResultMap.Builder(configuration, statementId + "-Inline", resultType,
                new ArrayList<>(), null).build();
            resultMaps.add(inlineResultMap);
        }
        return resultMaps;
    }

    public ResultMapping buildResultMapping(Class<?> resultType, String property, String column, Class<?> javaType,
                                            JdbcType jdbcType, String nestedSelect, String nestedResultMap, String notNullColumn, String columnPrefix,
                                            Class<? extends TypeHandler<?>> typeHandler, List<ResultFlag> flags, String resultSet, String foreignColumn,
                                            boolean lazy) {
        Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
        TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
        List<ResultMapping> composites;
        if ((nestedSelect == null || nestedSelect.isEmpty()) && (foreignColumn == null || foreignColumn.isEmpty())) {
            composites = Collections.emptyList();
        } else {
            composites = parseCompositeColumnName(column);
        }
        return new ResultMapping.Builder(configuration, property, column, javaTypeClass).jdbcType(jdbcType)
            .nestedQueryId(applyCurrentNamespace(nestedSelect, true))
            .nestedResultMapId(applyCurrentNamespace(nestedResultMap, true)).resultSet(resultSet)
            .typeHandler(typeHandlerInstance).flags(flags == null ? new ArrayList<>() : flags).composites(composites)
            .notNullColumns(parseMultipleColumnNames(notNullColumn)).columnPrefix(columnPrefix).foreignColumn(foreignColumn)
            .lazy(lazy).build();
    }

    /**
     * Backward compatibility signature 'buildResultMapping'.
     *
     * @param resultType      the result type
     * @param property        the property
     * @param column          the column
     * @param javaType        the java type
     * @param jdbcType        the jdbc type
     * @param nestedSelect    the nested select
     * @param nestedResultMap the nested result map
     * @param notNullColumn   the not null column
     * @param columnPrefix    the column prefix
     * @param typeHandler     the type handler
     * @param flags           the flags
     * @return the result mapping
     */
    public ResultMapping buildResultMapping(Class<?> resultType, String property, String column, Class<?> javaType,
                                            JdbcType jdbcType, String nestedSelect, String nestedResultMap, String notNullColumn, String columnPrefix,
                                            Class<? extends TypeHandler<?>> typeHandler, List<ResultFlag> flags) {
        return buildResultMapping(resultType, property, column, javaType, jdbcType, nestedSelect, nestedResultMap,
            notNullColumn, columnPrefix, typeHandler, flags, null, null, configuration.isLazyLoadingEnabled());
    }

    /**
     * Gets the language driver.
     *
     * @param langClass the lang class
     * @return the language driver
     * @deprecated Use {@link Configuration#getLanguageDriver(Class)}
     */
    @Deprecated
    public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
        return configuration.getLanguageDriver(langClass);
    }

    private Set<String> parseMultipleColumnNames(String columnName) {
        Set<String> columns = new HashSet<>();
        if (columnName != null) {
            if (columnName.indexOf(',') > -1) {
                StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
                while (parser.hasMoreTokens()) {
                    String column = parser.nextToken();
                    columns.add(column);
                }
            } else {
                columns.add(columnName);
            }
        }
        return columns;
    }

    private List<ResultMapping> parseCompositeColumnName(String columnName) {
        List<ResultMapping> composites = new ArrayList<>();
        if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
            StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
            while (parser.hasMoreTokens()) {
                String property = parser.nextToken();
                String column = parser.nextToken();
                ResultMapping complexResultMapping = new ResultMapping.Builder(configuration, property, column,
                    configuration.getTypeHandlerRegistry().getUnknownTypeHandler()).build();
                composites.add(complexResultMapping);
            }
        }
        return composites;
    }

    private Class<?> resolveResultJavaType(Class<?> resultType, String property, Class<?> javaType) {
        if (javaType == null && property != null) {
            try {
                MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
                javaType = metaResultType.getSetterType(property);
            } catch (Exception e) {
                // ignore, following null check statement will deal with the situation
            }
        }
        if (javaType == null) {
            javaType = Object.class;
        }
        return javaType;
    }

    private Class<?> resolveParameterJavaType(Class<?> resultType, String property, Class<?> javaType,
                                              JdbcType jdbcType) {
        if (javaType == null) {
            if (JdbcType.CURSOR.equals(jdbcType)) {
                javaType = java.sql.ResultSet.class;
            } else if (Map.class.isAssignableFrom(resultType)) {
                javaType = Object.class;
            } else {
                MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
                javaType = metaResultType.getGetterType(property);
            }
        }
        if (javaType == null) {
            javaType = Object.class;
        }
        return javaType;
    }

}
