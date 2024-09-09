/*
 *    Copyright 2009-2024 the original author or authors.
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
package org.apache.ibatis.builder.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.CacheNamespaceRef;
import org.apache.ibatis.annotations.Case;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Options.FlushCachePolicy;
import org.apache.ibatis.annotations.Property;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.TypeDiscriminator;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.FetchType;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class MapperAnnotationBuilder {

    private static final Set<Class<? extends Annotation>> statementAnnotationTypes = Stream
        .of(Select.class, Update.class, Insert.class, Delete.class, SelectProvider.class, UpdateProvider.class,
            InsertProvider.class, DeleteProvider.class)
        .collect(Collectors.toSet());

    private final Configuration configuration;
    private final MapperBuilderAssistant assistant;
    private final Class<?> type;

    public MapperAnnotationBuilder(Configuration configuration, Class<?> type) {
        String resource = type.getName().replace('.', '/') + ".java (best guess)";
        this.assistant = new MapperBuilderAssistant(configuration, resource);
        this.configuration = configuration;
        this.type = type;
    }

    /**
     * 解析功能的主要方法
     * 本方法负责根据当前类型和配置解析XML资源，并加载到配置中
     * 它还会初始化和解析缓存、缓存引用以及所有可解析的方法
     */
    public void parse() {
        // 获取当前要解析的资源标识
        String resource = type.toString();
        // 检查该资源是否已经被加载过
        if (!configuration.isResourceLoaded(resource)) {
            // 如果没有被加载过，则加载XML资源
            loadXmlResource();
            // 将该资源标记为已加载
            configuration.addLoadedResource(resource);
            // 设置当前命名空间
            assistant.setCurrentNamespace(type.getName());
            // 解析缓存配置
            parseCache();
            // 解析缓存引用配置
            parseCacheRef();
            // 遍历当前类型的所有方法
            for (Method method : type.getMethods()) {
                // 检查方法是否可以有Statement
                if (!canHaveStatement(method)) {
                    continue;
                }
                // 检查方法是否具有Select或SelectProvider注解，且没有ResultMap注解
                if (getAnnotationWrapper(method, false, Select.class, SelectProvider.class).isPresent()
                    && method.getAnnotation(ResultMap.class) == null) {
                    // 解析ResultMap
                    parseResultMap(method);
                }
                try {
                    // 解析Statement
                    parseStatement(method);
                } catch (IncompleteElementException e) {
                    // 如果解析不完整，则将方法添加到待解析列表
                    configuration.addIncompleteMethod(new MethodResolver(this, method));
                }
            }
        }
        // 解析剩余的不完整方法
        configuration.parsePendingMethods(false);
    }


    private static boolean canHaveStatement(Method method) {
        // issue #237
        return !method.isBridge() && !method.isDefault();
    }

    /**
     * 加载XML资源
     * 本方法负责从资源文件或类路径中加载XML映射文件
     * 如果Spring无法确定实际的资源名称，我们通过检查一个标志来防止重复加载资源
     * 该标志在XMLMapperBuilder的bindMapperForNamespace方法中设置
     */
    private void loadXmlResource() {
        // 检查资源是否已加载，避免重复加载
        if (!configuration.isResourceLoaded("namespace:" + type.getName())) {
            // 根据类名生成XML资源文件名
            String xmlResource = type.getName().replace('.', '/') + ".xml";
            // 尝试从资源文件流中加载XML映射
            InputStream inputStream = type.getResourceAsStream("/" + xmlResource);
            if (inputStream == null) {
                // 如果资源不在模块中，尝试从类路径中加载XML映射
                try {
                    inputStream = Resources.getResourceAsStream(type.getClassLoader(), xmlResource);
                } catch (IOException e2) {
                    // 忽略异常，资源不是必需的
                }
            }
            // 如果成功获取到资源流，解析XML映射
            if (inputStream != null) {
                XMLMapperBuilder xmlParser = new XMLMapperBuilder(inputStream, assistant.getConfiguration(), xmlResource,
                    configuration.getSqlFragments(), type.getName());
                xmlParser.parse();
            }
        }
    }


    /**
     * 解析缓存配置
     * 本方法负责解析类型上的缓存命名空间注解，并根据解析到的配置来初始化缓存实例
     */
    private void parseCache() {
        // 获取类型上的缓存命名空间注解
        CacheNamespace cacheDomain = type.getAnnotation(CacheNamespace.class);
        if (cacheDomain != null) {
            // 解析缓存大小，若未指定则为null
            Integer size = cacheDomain.size() == 0 ? null : cacheDomain.size();
            // 解析缓存刷新间隔，若未指定则为null
            Long flushInterval = cacheDomain.flushInterval() == 0 ? null : cacheDomain.flushInterval();
            // 将缓存属性转换为Properties对象
            Properties props = convertToProperties(cacheDomain.properties());
            // 使用解析到的配置初始化新的缓存实例
            assistant.useNewCache(cacheDomain.implementation(), cacheDomain.eviction(), flushInterval, size,
                cacheDomain.readWrite(), cacheDomain.blocking(), props);
        }
    }


    /**
     * 将Property数组转换为Java的Properties对象
     *
     * @param properties Property数组，包含键值对
     * @return Properties对象，如果输入数组为空，则返回null
     */
    private Properties convertToProperties(Property[] properties) {
        // 当属性数组为空时，直接返回null以节省资源
        if (properties.length == 0) {
            return null;
        }
        // 初始化Properties对象用于存储键值对
        Properties props = new Properties();
        // 遍历属性数组，将每个键值对添加到Properties对象中
        for (Property property : properties) {
            // 使用PropertyParser解析属性值，并将解析后的值存入Properties对象
            props.setProperty(property.name(), PropertyParser.parse(property.value(), configuration.getVariables()));
        }
        // 返回填充了键值对的Properties对象
        return props;
    }

    private void parseCacheRef() {
        CacheNamespaceRef cacheDomainRef = type.getAnnotation(CacheNamespaceRef.class);
        if (cacheDomainRef != null) {
            Class<?> refType = cacheDomainRef.value();
            String refName = cacheDomainRef.name();
            if (refType == void.class && refName.isEmpty()) {
                throw new BuilderException("Should be specified either value() or name() attribute in the @CacheNamespaceRef");
            }
            if (refType != void.class && !refName.isEmpty()) {
                throw new BuilderException("Cannot use both value() and name() attribute in the @CacheNamespaceRef");
            }
            String namespace = refType != void.class ? refType.getName() : refName;
            try {
                assistant.useCacheRef(namespace);
            } catch (IncompleteElementException e) {
                configuration.addIncompleteCacheRef(new CacheRefResolver(assistant, namespace));
            }
        }
    }

    private String parseResultMap(Method method) {
        Class<?> returnType = getReturnType(method, type);
        Arg[] args = method.getAnnotationsByType(Arg.class);
        Result[] results = method.getAnnotationsByType(Result.class);
        TypeDiscriminator typeDiscriminator = method.getAnnotation(TypeDiscriminator.class);
        String resultMapId = generateResultMapName(method);
        applyResultMap(resultMapId, returnType, args, results, typeDiscriminator);
        return resultMapId;
    }

    private String generateResultMapName(Method method) {
        Results results = method.getAnnotation(Results.class);
        if (results != null && !results.id().isEmpty()) {
            return type.getName() + "." + results.id();
        }
        StringBuilder suffix = new StringBuilder();
        for (Class<?> c : method.getParameterTypes()) {
            suffix.append("-");
            suffix.append(c.getSimpleName());
        }
        if (suffix.length() < 1) {
            suffix.append("-void");
        }
        return type.getName() + "." + method.getName() + suffix;
    }

    private void applyResultMap(String resultMapId, Class<?> returnType, Arg[] args, Result[] results,
                                TypeDiscriminator discriminator) {
        List<ResultMapping> resultMappings = new ArrayList<>();
        applyConstructorArgs(args, returnType, resultMappings);
        applyResults(results, returnType, resultMappings);
        Discriminator disc = applyDiscriminator(resultMapId, returnType, discriminator);
        // TODO add AutoMappingBehaviour
        assistant.addResultMap(resultMapId, returnType, null, disc, resultMappings, null);
        createDiscriminatorResultMaps(resultMapId, returnType, discriminator);
    }

    private void createDiscriminatorResultMaps(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
        if (discriminator != null) {
            for (Case c : discriminator.cases()) {
                String caseResultMapId = resultMapId + "-" + c.value();
                List<ResultMapping> resultMappings = new ArrayList<>();
                // issue #136
                applyConstructorArgs(c.constructArgs(), resultType, resultMappings);
                applyResults(c.results(), resultType, resultMappings);
                // TODO add AutoMappingBehaviour
                assistant.addResultMap(caseResultMapId, c.type(), resultMapId, null, resultMappings, null);
            }
        }
    }

    private Discriminator applyDiscriminator(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
        if (discriminator != null) {
            String column = discriminator.column();
            Class<?> javaType = discriminator.javaType() == void.class ? String.class : discriminator.javaType();
            JdbcType jdbcType = discriminator.jdbcType() == JdbcType.UNDEFINED ? null : discriminator.jdbcType();
            @SuppressWarnings("unchecked")
            Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>) (discriminator
                .typeHandler() == UnknownTypeHandler.class ? null : discriminator.typeHandler());
            Case[] cases = discriminator.cases();
            Map<String, String> discriminatorMap = new HashMap<>();
            for (Case c : cases) {
                String value = c.value();
                String caseResultMapId = resultMapId + "-" + value;
                discriminatorMap.put(value, caseResultMapId);
            }
            return assistant.buildDiscriminator(resultType, column, javaType, jdbcType, typeHandler, discriminatorMap);
        }
        return null;
    }

    /**
     * 解析映射语句
     *
     * @param method 要解析的方法
     */
    void parseStatement(Method method) {
        // 获取方法的参数类型
        final Class<?> parameterTypeClass = getParameterType(method);
        // 获取方法的语言驱动
        final LanguageDriver languageDriver = getLanguageDriver(method);

        // 获取方法上的语句注解（如@Select、@Insert等）
        getAnnotationWrapper(method, true, statementAnnotationTypes).ifPresent(statementAnnotation -> {
            // 构建SQL源
            final SqlSource sqlSource = buildSqlSource(statementAnnotation.getAnnotation(), parameterTypeClass,
                languageDriver, method);
            // 获取SQL命令类型（如INSERT、SELECT等）
            final SqlCommandType sqlCommandType = statementAnnotation.getSqlCommandType();
            // 获取方法上的Options注解
            final Options options = getAnnotationWrapper(method, false, Options.class).map(x -> (Options) x.getAnnotation())
                .orElse(null);
            // 构建映射语句的ID
            final String mappedStatementId = type.getName() + "." + method.getName();

            // 初始化主键生成器
            final KeyGenerator keyGenerator;
            String keyProperty = null;
            String keyColumn = null;
            // 根据SQL命令类型配置主键生成策略
            if (SqlCommandType.INSERT.equals(sqlCommandType) || SqlCommandType.UPDATE.equals(sqlCommandType)) {
                // 首先检查SelectKey注解，它优先于其他所有配置
                SelectKey selectKey = getAnnotationWrapper(method, false, SelectKey.class)
                    .map(x -> (SelectKey) x.getAnnotation()).orElse(null);
                if (selectKey != null) {
                    // 处理SelectKey注解
                    keyGenerator = handleSelectKeyAnnotation(selectKey, mappedStatementId, getParameterType(method),
                        languageDriver);
                    keyProperty = selectKey.keyProperty();
                } else if (options == null) {
                    // 如果没有Options注解，则根据配置决定使用Jdbc3KeyGenerator还是NoKeyGenerator
                    keyGenerator = configuration.isUseGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
                } else {
                    // 根据Options注解配置主键生成器
                    keyGenerator = options.useGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
                    keyProperty = options.keyProperty();
                    keyColumn = options.keyColumn();
                }
            } else {
                // 对于非INSERT和UPDATE操作，使用NoKeyGenerator
                keyGenerator = NoKeyGenerator.INSTANCE;
            }

            // 默认配置
            Integer fetchSize = null;
            Integer timeout = null;
            StatementType statementType = StatementType.PREPARED;
            ResultSetType resultSetType = configuration.getDefaultResultSetType();
            boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
            boolean flushCache = !isSelect;
            boolean useCache = isSelect;
            // 根据Options注解进一步配置
            if (options != null) {
                flushCache = FlushCachePolicy.TRUE.equals(options.flushCache()) ? true : FlushCachePolicy.FALSE.equals(options.flushCache()) ? false : flushCache;
                useCache = options.useCache();
                fetchSize = options.fetchSize() > -1 || options.fetchSize() == Integer.MIN_VALUE ? options.fetchSize() : null;
                timeout = options.timeout() > -1 ? options.timeout() : null;
                statementType = options.statementType();
                resultSetType = options.resultSetType() != ResultSetType.DEFAULT ? options.resultSetType() : resultSetType;
            }

            // 处理SELECT语句的结果映射
            String resultMapId = null;
            if (isSelect) {
                ResultMap resultMapAnnotation = method.getAnnotation(ResultMap.class);
                resultMapId = resultMapAnnotation != null ? String.join(",", resultMapAnnotation.value()) : generateResultMapName(method);
            }

            // 添加映射语句
            assistant.addMappedStatement(mappedStatementId, sqlSource, statementType, sqlCommandType, fetchSize, timeout,
                null, parameterTypeClass, resultMapId, getReturnType(method, type), resultSetType, flushCache, useCache,
                false, keyGenerator, keyProperty, keyColumn, statementAnnotation.getDatabaseId(), languageDriver,
                options != null ? nullOrEmpty(options.resultSets()) : null, statementAnnotation.isDirtySelect());
        });
    }


    private LanguageDriver getLanguageDriver(Method method) {
        Lang lang = method.getAnnotation(Lang.class);
        Class<? extends LanguageDriver> langClass = null;
        if (lang != null) {
            langClass = lang.value();
        }
        return configuration.getLanguageDriver(langClass);
    }

    /**
     * 获取方法的参数类型
     * 此方法旨在解析方法的参数类型，尤其关注那些不是RowBounds或ResultHandler类型的参数
     * 如果存在多个此类参数，则返回ParamMap类，以应对多参数情况
     *
     * @param method 反射方法，用于获取参数类型
     * @return 解析后的参数类型，可能是单个参数类型或ParamMap类
     */
    private Class<?> getParameterType(Method method) {
        // 初始化参数类型为null
        Class<?> parameterType = null;
        // 获取方法的所有参数类型
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 遍历所有参数类型
        for (Class<?> currentParameterType : parameterTypes) {
            // 排除RowBounds和ResultHandler类，它们可能作为特殊参数处理
            if (!RowBounds.class.isAssignableFrom(currentParameterType)
                && !ResultHandler.class.isAssignableFrom(currentParameterType)) {
                // 如果parameterType尚未初始化，则初始化为当前参数类型
                if (parameterType == null) {
                    parameterType = currentParameterType;
                } else {
                    // 如果已存在parameterType且有其他参数类型，则设置为ParamMap类
                    // 这是为了处理有多个参数的情况
                    parameterType = ParamMap.class;
                }
            }
        }
        // 返回解析得到的参数类型
        return parameterType;
    }


    private static Class<?> getReturnType(Method method, Class<?> type) {
        Class<?> returnType = method.getReturnType();
        Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, type);
        if (resolvedReturnType instanceof Class) {
            returnType = (Class<?>) resolvedReturnType;
            if (returnType.isArray()) {
                returnType = returnType.getComponentType();
            }
            // gcode issue #508
            if (void.class.equals(returnType)) {
                ResultType rt = method.getAnnotation(ResultType.class);
                if (rt != null) {
                    returnType = rt.value();
                }
            }
        } else if (resolvedReturnType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) resolvedReturnType;
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            if (Collection.class.isAssignableFrom(rawType) || Cursor.class.isAssignableFrom(rawType)) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments != null && actualTypeArguments.length == 1) {
                    Type returnTypeParameter = actualTypeArguments[0];
                    if (returnTypeParameter instanceof Class<?>) {
                        returnType = (Class<?>) returnTypeParameter;
                    } else if (returnTypeParameter instanceof ParameterizedType) {
                        // (gcode issue #443) actual type can be a also a parameterized type
                        returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
                    } else if (returnTypeParameter instanceof GenericArrayType) {
                        Class<?> componentType = (Class<?>) ((GenericArrayType) returnTypeParameter).getGenericComponentType();
                        // (gcode issue #525) support List<byte[]>
                        returnType = Array.newInstance(componentType, 0).getClass();
                    }
                }
            } else if (method.isAnnotationPresent(MapKey.class) && Map.class.isAssignableFrom(rawType)) {
                // (gcode issue 504) Do not look into Maps if there is not MapKey annotation
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments != null && actualTypeArguments.length == 2) {
                    Type returnTypeParameter = actualTypeArguments[1];
                    if (returnTypeParameter instanceof Class<?>) {
                        returnType = (Class<?>) returnTypeParameter;
                    } else if (returnTypeParameter instanceof ParameterizedType) {
                        // (gcode issue 443) actual type can be a also a parameterized type
                        returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
                    }
                }
            } else if (Optional.class.equals(rawType)) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                Type returnTypeParameter = actualTypeArguments[0];
                if (returnTypeParameter instanceof Class<?>) {
                    returnType = (Class<?>) returnTypeParameter;
                }
            }
        }

        return returnType;
    }

    private void applyResults(Result[] results, Class<?> resultType, List<ResultMapping> resultMappings) {
        for (Result result : results) {
            List<ResultFlag> flags = new ArrayList<>();
            if (result.id()) {
                flags.add(ResultFlag.ID);
            }
            @SuppressWarnings("unchecked")
            Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>) (result
                .typeHandler() == UnknownTypeHandler.class ? null : result.typeHandler());
            boolean hasNestedResultMap = hasNestedResultMap(result);
            ResultMapping resultMapping = assistant.buildResultMapping(resultType, nullOrEmpty(result.property()),
                nullOrEmpty(result.column()), result.javaType() == void.class ? null : result.javaType(),
                result.jdbcType() == JdbcType.UNDEFINED ? null : result.jdbcType(),
                hasNestedSelect(result) ? nestedSelectId(result) : null,
                hasNestedResultMap ? nestedResultMapId(result) : null, null,
                hasNestedResultMap ? findColumnPrefix(result) : null, typeHandler, flags, null, null, isLazy(result));
            resultMappings.add(resultMapping);
        }
    }

    private String findColumnPrefix(Result result) {
        String columnPrefix = result.one().columnPrefix();
        if (columnPrefix.length() < 1) {
            columnPrefix = result.many().columnPrefix();
        }
        return columnPrefix;
    }

    private String nestedResultMapId(Result result) {
        String resultMapId = result.one().resultMap();
        if (resultMapId.length() < 1) {
            resultMapId = result.many().resultMap();
        }
        if (!resultMapId.contains(".")) {
            resultMapId = type.getName() + "." + resultMapId;
        }
        return resultMapId;
    }

    private boolean hasNestedResultMap(Result result) {
        if (result.one().resultMap().length() > 0 && result.many().resultMap().length() > 0) {
            throw new BuilderException("Cannot use both @One and @Many annotations in the same @Result");
        }
        return result.one().resultMap().length() > 0 || result.many().resultMap().length() > 0;
    }

    private String nestedSelectId(Result result) {
        String nestedSelect = result.one().select();
        if (nestedSelect.length() < 1) {
            nestedSelect = result.many().select();
        }
        if (!nestedSelect.contains(".")) {
            nestedSelect = type.getName() + "." + nestedSelect;
        }
        return nestedSelect;
    }

    private boolean isLazy(Result result) {
        boolean isLazy = configuration.isLazyLoadingEnabled();
        if (result.one().select().length() > 0 && FetchType.DEFAULT != result.one().fetchType()) {
            isLazy = result.one().fetchType() == FetchType.LAZY;
        } else if (result.many().select().length() > 0 && FetchType.DEFAULT != result.many().fetchType()) {
            isLazy = result.many().fetchType() == FetchType.LAZY;
        }
        return isLazy;
    }

    private boolean hasNestedSelect(Result result) {
        if (result.one().select().length() > 0 && result.many().select().length() > 0) {
            throw new BuilderException("Cannot use both @One and @Many annotations in the same @Result");
        }
        return result.one().select().length() > 0 || result.many().select().length() > 0;
    }

    private void applyConstructorArgs(Arg[] args, Class<?> resultType, List<ResultMapping> resultMappings) {
        for (Arg arg : args) {
            List<ResultFlag> flags = new ArrayList<>();
            flags.add(ResultFlag.CONSTRUCTOR);
            if (arg.id()) {
                flags.add(ResultFlag.ID);
            }
            @SuppressWarnings("unchecked")
            Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>) (arg
                .typeHandler() == UnknownTypeHandler.class ? null : arg.typeHandler());
            ResultMapping resultMapping = assistant.buildResultMapping(resultType, nullOrEmpty(arg.name()),
                nullOrEmpty(arg.column()), arg.javaType() == void.class ? null : arg.javaType(),
                arg.jdbcType() == JdbcType.UNDEFINED ? null : arg.jdbcType(), nullOrEmpty(arg.select()),
                nullOrEmpty(arg.resultMap()), null, nullOrEmpty(arg.columnPrefix()), typeHandler, flags, null, null, false);
            resultMappings.add(resultMapping);
        }
    }

    private String nullOrEmpty(String value) {
        return value == null || value.trim().length() == 0 ? null : value;
    }

    private KeyGenerator handleSelectKeyAnnotation(SelectKey selectKeyAnnotation, String baseStatementId,
                                                   Class<?> parameterTypeClass, LanguageDriver languageDriver) {
        String id = baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
        Class<?> resultTypeClass = selectKeyAnnotation.resultType();
        StatementType statementType = selectKeyAnnotation.statementType();
        String keyProperty = selectKeyAnnotation.keyProperty();
        String keyColumn = selectKeyAnnotation.keyColumn();
        boolean executeBefore = selectKeyAnnotation.before();

        // defaults
        boolean useCache = false;
        KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
        Integer fetchSize = null;
        Integer timeout = null;
        boolean flushCache = false;
        String parameterMap = null;
        String resultMap = null;
        ResultSetType resultSetTypeEnum = null;
        String databaseId = selectKeyAnnotation.databaseId().isEmpty() ? null : selectKeyAnnotation.databaseId();

        SqlSource sqlSource = buildSqlSource(selectKeyAnnotation, parameterTypeClass, languageDriver, null);
        SqlCommandType sqlCommandType = SqlCommandType.SELECT;

        assistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap,
            parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum, flushCache, useCache, false, keyGenerator,
            keyProperty, keyColumn, databaseId, languageDriver, null, false);

        id = assistant.applyCurrentNamespace(id, false);

        MappedStatement keyStatement = configuration.getMappedStatement(id, false);
        SelectKeyGenerator answer = new SelectKeyGenerator(keyStatement, executeBefore);
        configuration.addKeyGenerator(id, answer);
        return answer;
    }

    private SqlSource buildSqlSource(Annotation annotation, Class<?> parameterType, LanguageDriver languageDriver,
                                     Method method) {
        if (annotation instanceof Select) {
            return buildSqlSourceFromStrings(((Select) annotation).value(), parameterType, languageDriver);
        }
        if (annotation instanceof Update) {
            return buildSqlSourceFromStrings(((Update) annotation).value(), parameterType, languageDriver);
        } else if (annotation instanceof Insert) {
            return buildSqlSourceFromStrings(((Insert) annotation).value(), parameterType, languageDriver);
        } else if (annotation instanceof Delete) {
            return buildSqlSourceFromStrings(((Delete) annotation).value(), parameterType, languageDriver);
        } else if (annotation instanceof SelectKey) {
            return buildSqlSourceFromStrings(((SelectKey) annotation).statement(), parameterType, languageDriver);
        }
        return new ProviderSqlSource(assistant.getConfiguration(), annotation, type, method);
    }

    private SqlSource buildSqlSourceFromStrings(String[] strings, Class<?> parameterTypeClass,
                                                LanguageDriver languageDriver) {
        return languageDriver.createSqlSource(configuration, String.join(" ", strings).trim(), parameterTypeClass);
    }

    @SafeVarargs
    private final Optional<AnnotationWrapper> getAnnotationWrapper(Method method, boolean errorIfNoMatch,
                                                                   Class<? extends Annotation>... targetTypes) {
        return getAnnotationWrapper(method, errorIfNoMatch, Arrays.asList(targetTypes));
    }

    private Optional<AnnotationWrapper> getAnnotationWrapper(Method method, boolean errorIfNoMatch,
                                                             Collection<Class<? extends Annotation>> targetTypes) {
        String databaseId = configuration.getDatabaseId();
        Map<String, AnnotationWrapper> statementAnnotations = targetTypes.stream()
            .flatMap(x -> Arrays.stream(method.getAnnotationsByType(x))).map(AnnotationWrapper::new)
            .collect(Collectors.toMap(AnnotationWrapper::getDatabaseId, x -> x, (existing, duplicate) -> {
                throw new BuilderException(
                    String.format("Detected conflicting annotations '%s' and '%s' on '%s'.", existing.getAnnotation(),
                        duplicate.getAnnotation(), method.getDeclaringClass().getName() + "." + method.getName()));
            }));
        AnnotationWrapper annotationWrapper = null;
        if (databaseId != null) {
            annotationWrapper = statementAnnotations.get(databaseId);
        }
        if (annotationWrapper == null) {
            annotationWrapper = statementAnnotations.get("");
        }
        if (errorIfNoMatch && annotationWrapper == null && !statementAnnotations.isEmpty()) {
            // Annotations exist, but there is no matching one for the specified databaseId
            throw new BuilderException(String.format(
                "Could not find a statement annotation that correspond a current database or default statement on method '%s.%s'. Current database id is [%s].",
                method.getDeclaringClass().getName(), method.getName(), databaseId));
        }
        return Optional.ofNullable(annotationWrapper);
    }

    public static Class<?> getMethodReturnType(String mapperFqn, String localStatementId) {
        if (mapperFqn == null || localStatementId == null) {
            return null;
        }
        try {
            Class<?> mapperClass = Resources.classForName(mapperFqn);
            for (Method method : mapperClass.getMethods()) {
                if (method.getName().equals(localStatementId) && canHaveStatement(method)) {
                    return getReturnType(method, mapperClass);
                }
            }
        } catch (ClassNotFoundException e) {
            // No corresponding mapper interface which is OK
        }
        return null;
    }

    private static class AnnotationWrapper {
        private final Annotation annotation;
        private final String databaseId;
        private final SqlCommandType sqlCommandType;
        private boolean dirtySelect;

        AnnotationWrapper(Annotation annotation) {
            this.annotation = annotation;
            if (annotation instanceof Select) {
                databaseId = ((Select) annotation).databaseId();
                sqlCommandType = SqlCommandType.SELECT;
                dirtySelect = ((Select) annotation).affectData();
            } else if (annotation instanceof Update) {
                databaseId = ((Update) annotation).databaseId();
                sqlCommandType = SqlCommandType.UPDATE;
            } else if (annotation instanceof Insert) {
                databaseId = ((Insert) annotation).databaseId();
                sqlCommandType = SqlCommandType.INSERT;
            } else if (annotation instanceof Delete) {
                databaseId = ((Delete) annotation).databaseId();
                sqlCommandType = SqlCommandType.DELETE;
            } else if (annotation instanceof SelectProvider) {
                databaseId = ((SelectProvider) annotation).databaseId();
                sqlCommandType = SqlCommandType.SELECT;
                dirtySelect = ((SelectProvider) annotation).affectData();
            } else if (annotation instanceof UpdateProvider) {
                databaseId = ((UpdateProvider) annotation).databaseId();
                sqlCommandType = SqlCommandType.UPDATE;
            } else if (annotation instanceof InsertProvider) {
                databaseId = ((InsertProvider) annotation).databaseId();
                sqlCommandType = SqlCommandType.INSERT;
            } else if (annotation instanceof DeleteProvider) {
                databaseId = ((DeleteProvider) annotation).databaseId();
                sqlCommandType = SqlCommandType.DELETE;
            } else {
                sqlCommandType = SqlCommandType.UNKNOWN;
                if (annotation instanceof Options) {
                    databaseId = ((Options) annotation).databaseId();
                } else if (annotation instanceof SelectKey) {
                    databaseId = ((SelectKey) annotation).databaseId();
                } else {
                    databaseId = "";
                }
            }
        }

        Annotation getAnnotation() {
            return annotation;
        }

        SqlCommandType getSqlCommandType() {
            return sqlCommandType;
        }

        String getDatabaseId() {
            return databaseId;
        }

        boolean isDirtySelect() {
            return dirtySelect;
        }
    }
}
