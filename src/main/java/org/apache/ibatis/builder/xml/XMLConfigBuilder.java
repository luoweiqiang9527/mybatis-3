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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

    private boolean parsed;
    private final XPathParser parser;
    private String environment;
    private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(Configuration.class, reader, environment, props);
    }

    public XMLConfigBuilder(Class<? extends Configuration> configClass, Reader reader, String environment,
                            Properties props) {
        this(configClass, new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(Configuration.class, inputStream, environment, props);
    }

    public XMLConfigBuilder(Class<? extends Configuration> configClass, InputStream inputStream, String environment,
                            Properties props) {
        this(configClass, new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    private XMLConfigBuilder(Class<? extends Configuration> configClass, XPathParser parser, String environment,
                             Properties props) {
        super(newConfig(configClass));
        ErrorContext.instance().resource("SQL Mapper Configuration");
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    /**
     * 解析XML配置文件并生成Configuration实例
     * 该方法确保XMLConfigBuilder实例只能被解析一次
     *
     * @return Configuration 解析后的Configuration实例
     * @throws BuilderException 如果尝试解析超过一次
     */
    public Configuration parse() {
        // 确保XMLConfigBuilder实例只能解析一次
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        // 标记为已解析，防止重复解析
        parsed = true;
        // 实际执行解析操作
        parseConfiguration(parser.evalNode("/configuration"));
        // 返回解析得到的Configuration实例
        return configuration;
    }

    /**
     * 解析配置文件的根节点，加载和配置各种元素。
     *
     * @param root 配置文件的根节点，用于从中提取和配置各项设置。
     */
    private void parseConfiguration(XNode root) {
        try {
            // 优先读取属性配置
            // issue #117 read properties first
            propertiesElement(root.evalNode("properties"));
            // 加载和配置设置
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            loadCustomVfsImpl(settings);
            loadCustomLogImpl(settings);
            // 处理类型别名配置
            typeAliasesElement(root.evalNode("typeAliases"));
            // 处理插件配置
            pluginsElement(root.evalNode("plugins"));
            // 处理对象工厂配置
            objectFactoryElement(root.evalNode("objectFactory"));
            // 处理对象包装器工厂配置
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            // 处理反射器工厂配置
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            // 应用全局设置
            settingsElement(settings);
            // 处理环境配置，需在对象工厂和对象包装器工厂之后
            // read it after objectFactory and objectWrapperFactory issue #631
            environmentsElement(root.evalNode("environments"));
            // 处理数据库ID提供者配置
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            // 处理类型处理器配置
            typeHandlersElement(root.evalNode("typeHandlers"));
            // 处理映射器配置
            mappersElement(root.evalNode("mappers"));
        } catch (Exception e) {
            // 解析配置出错时，抛出构建器异常
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }


    private Properties settingsAsProperties(XNode context) {
        if (context == null) {
            return new Properties();
        }
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        for (Object key : props.keySet()) {
            if (!metaConfig.hasSetter(String.valueOf(key))) {
                throw new BuilderException(
                    "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
            }
        }
        return props;
    }

    /**
     * 加载自定义的VFS实现类
     * <p>
     * 通过解析配置属性中的vfsImpl属性值，来加载用户指定的VFS实现类
     * 这个方法允许系统管理员或者用户通过外部配置文件指定的方式，来扩展或替换系统内置的VFS实现
     *
     * @param props 外部配置属性，用于获取vfsImpl属性值
     * @throws ClassNotFoundException 当指定的VFS实现类不存在时抛出此异常
     */
    private void loadCustomVfsImpl(Properties props) throws ClassNotFoundException {
        // 获取vfsImpl属性值，如果未设置则退出方法
        String value = props.getProperty("vfsImpl");
        if (value == null) {
            return;
        }

        // 将vfsImpl属性值按逗号分割成类名数组
        String[] clazzes = value.split(",");

        // 遍历类名数组，尝试加载每个类名对应的VFS实现类
        for (String clazz : clazzes) {
            // 忽略空的类名
            if (!clazz.isEmpty()) {
                // 根据类名加载VFS实现类，这里使用Resources类的classForName静态方法转换类名为Class对象
                // 由于强转为Class<? extends VFS>，Java会发出unchecked警告，因此使用@SuppressWarnings抑制警告
                @SuppressWarnings("unchecked")
                Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);

                // 将加载到的VFS实现类设置到configuration中，替换或扩展默认的VFS实现
                configuration.setVfsImpl(vfsImpl);
            }
        }
    }

    private void loadCustomLogImpl(Properties props) {
        Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
        configuration.setLogImpl(logImpl);
    }

    /**
     * 解析typeAliases配置元素
     * 该方法负责处理typeAliases相关的配置，通过解析配置文件中的标签来注册类型别名
     * 类型别名可以简化映射器(Mapper)的配置，使得配置更加清晰
     *
     * @param context 代表typeAliases配置元素的上下文，包含一系列子元素每个子元素代表一个类型别名的定义
     */
    private void typeAliasesElement(XNode context) {
        // 检查context是否为null，避免空指针异常
        if (context == null) {
            return;
        }
        // 遍历typeAliases元素下的所有子元素
        for (XNode child : context.getChildren()) {
            // 处理package元素，自动扫描包中的所有类并注册为类型别名
            if ("package".equals(child.getName())) {
                String typeAliasPackage = child.getStringAttribute("name");
                configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
            } else {
                // 处理单个类型别名元素
                String alias = child.getStringAttribute("alias");
                String type = child.getStringAttribute("type");
                try {
                    // 根据类型名称获取Class对象
                    Class<?> clazz = Resources.classForName(type);
                    // 根据是否存在别名属性，注册类型别名
                    if (alias == null) {
                        typeAliasRegistry.registerAlias(clazz);
                    } else {
                        typeAliasRegistry.registerAlias(alias, clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // 如果类型不存在，抛出异常
                    throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                }
            }
        }
    }


    /**
     * 解析插件元素
     * 该方法负责处理XML配置中的plugins元素，创建拦截器实例并添加到配置中
     *
     * @param context 描述plugins元素的XNode对象
     * @throws Exception 如果拦截器实例创建或设置属性过程中发生错误
     */
    private void pluginsElement(XNode context) throws Exception {
        // 确保传入的context对象不为null
        if (context != null) {
            // 遍历context下的所有子节点，每个子节点代表一个插件配置
            for (XNode child : context.getChildren()) {
                // 获取插件的拦截器类名
                String interceptor = child.getStringAttribute("interceptor");
                // 将插件的配置属性作为Properties对象收集
                Properties properties = child.getChildrenAsProperties();

                // 通过类名解析并实例化拦截器对象
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor()
                    .newInstance();
                // 为拦截器实例设置属性
                interceptorInstance.setProperties(properties);
                // 将拦截器实例添加到配置中
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }


    /**
     * 根据配置文件中的对象工厂元素配置 ObjectFactory
     * ObjectFactory 负责创建映射器对象，它可以在创建对象时注入依赖
     *
     * @param context XNode 对象，表示对象工厂的配置节点
     * @throws Exception 如果配置的工厂类型无法解析或实例化失败
     */
    private void objectFactoryElement(XNode context) throws Exception {
        // 检查配置节点是否为空
        if (context != null) {
            // 获取对象工厂的类型
            String type = context.getStringAttribute("type");
            // 将配置节点的子元素转换为属性集合
            Properties properties = context.getChildrenAsProperties();
            // 解析类名并实例化对象工厂
            ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            // 设置对象工厂的属性
            factory.setProperties(properties);
            // 将对象工厂配置到环境配置对象中
            configuration.setObjectFactory(factory);
        }
    }


    /**
     * 根据配置信息初始化对象包装器工厂
     * 该方法通过解析配置文件中的<type>属性来创建对应类型的ObjectWrapperFactory实例，并将其设置到配置对象中
     *
     * @param context 表示配置信息的XNode对象，用于获取对象包装器工厂的类型信息
     * @throws Exception 如果在解析类或创建实例过程中发生错误，则抛出异常
     */
    private void objectWrapperFactoryElement(XNode context) throws Exception {
        // 检查上下文是否为null，如果为null则不执行任何操作
        if (context != null) {
            // 获取配置文件中指定的工厂类型
            String type = context.getStringAttribute("type");
            // 根据类型名称解析对应的类，并使用默认构造函数创建ObjectWrapperFactory的实例
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            // 将创建的ObjectWrapperFactory实例设置到配置对象中
            configuration.setObjectWrapperFactory(factory);
        }
    }


    /**
     * 解析reflectorFactory配置元素并实例化相应的ReflectorFactory对象
     *
     * @param context XNode对象，表示reflectorFactory配置元素的上下文
     * @throws Exception 如果在解析或实例化过程中发生错误，则抛出异常
     */
    private void reflectorFactoryElement(XNode context) throws Exception {
        // 检查context是否为null，如果为null则不执行任何操作
        if (context != null) {
            // 获取reflectorFactory的类型属性值
            String type = context.getStringAttribute("type");
            // 根据类型属性值解析并实例化对应的ReflectorFactory类
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            // 将实例化的ReflectorFactory设置到配置对象中
            configuration.setReflectorFactory(factory);
        }
    }


    /**
     * 处理属性元素配置。
     * 从给定的XNode对象中加载属性配置，这些属性可以来自资源文件、URL或已有的属性集合。
     * 如果同时指定了资源(resource)和URL(url)，则抛出异常，因为两者不能同时使用。
     * 最后，将加载的属性应用到解析器和配置对象中。
     *
     * @param context XNode对象，包含属性元素的配置信息。
     * @throws Exception 如果同时指定了资源和URL，则抛出BuilderException异常。
     */
    private void propertiesElement(XNode context) throws Exception {
        // 如果上下文为空，则直接返回，不进行任何处理
        if (context == null) {
            return;
        }
        // 从上下文中加载子元素作为属性集合
        Properties defaults = context.getChildrenAsProperties();
        // 从上下文中获取resource属性和url属性的值
        String resource = context.getStringAttribute("resource");
        String url = context.getStringAttribute("url");
        // 如果同时指定了resource和url，则抛出异常
        if (resource != null && url != null) {
            throw new BuilderException(
                "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
        }
        // 如果指定了resource，则加载资源文件作为属性集合，并合并到默认属性中
        if (resource != null) {
            defaults.putAll(Resources.getResourceAsProperties(resource));
            // 如果指定了url，则加载URL指向的属性文件，并合并到默认属性中
        } else if (url != null) {
            defaults.putAll(Resources.getUrlAsProperties(url));
        }
        // 获取配置对象中的变量属性，并合并到默认属性中
        Properties vars = configuration.getVariables();
        if (vars != null) {
            defaults.putAll(vars);
        }
        // 将合并后的属性应用到解析器中
        parser.setVariables(defaults);
        // 将合并后的属性应用到配置对象中
        configuration.setVariables(defaults);
    }


    /**
     * 根据属性文件配置设置MyBatis配置项
     * 该方法通过读取Properties对象中的属性，来动态设置MyBatis的各种配置参数
     * 例如，自动映射行为、缓存启用状态、代理工厂等这些配置直接影响了MyBatis如何映射结果集、管理缓存以及处理SQL执行
     * 通过这种方式，可以在不修改代码的情况下，灵活地调整MyBatis的行为，满足不同环境或需求下的配置需求
     *
     * @param props 包含配置属性的Properties对象
     */
    private void settingsElement(Properties props) {
        configuration
            .setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
        configuration.setAutoMappingUnknownColumnBehavior(
            AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
        configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
        configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
        configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
        configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
        configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
        configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
        configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
        configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
        configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
        configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
        configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        configuration.setLazyLoadTriggerMethods(
            stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
        configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
        configuration.setArgNameBasedConstructorAutoMapping(
            booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
        configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
        configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
    }


    /**
     * 配置环境元素
     * 该方法负责解析给定的上下文节点中的环境配置，并设置到配置对象中
     * 它主要处理环境的默认设置、特定环境的识别以及事务和数据源工厂的配置
     *
     * @param context 表示环境配置的上下文节点
     * @throws Exception 当解析过程中发生错误时抛出异常
     */
    private void environmentsElement(XNode context) throws Exception {
        // 如果上下文节点为空，则直接返回，不进行任何配置
        if (context == null) {
            return;
        }
        // 如果环境变量未设置，则尝试从上下文节点的默认属性中获取
        if (environment == null) {
            environment = context.getStringAttribute("default");
        }
        // 遍历上下文节点的所有子节点，寻找匹配的环境配置
        for (XNode child : context.getChildren()) {
            // 获取当前子节点的id属性，用于标识环境
            String id = child.getStringAttribute("id");
            // 如果当前环境与指定的环境匹配，则进行配置
            if (isSpecifiedEnvironment(id)) {
                // 解析事务工厂配置
                TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                // 解析数据源工厂配置
                DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                // 获取配置的数据源实例
                DataSource dataSource = dsFactory.getDataSource();
                // 创建环境构建器，并配置事务工厂和数据源
                Environment.Builder environmentBuilder = new Environment.Builder(id).transactionFactory(txFactory)
                    .dataSource(dataSource);
                // 将构建的环境配置设置到配置对象中，并结束循环
                configuration.setEnvironment(environmentBuilder.build());
                break;
            }
        }
    }


    /**
     * 从XML配置中读取数据库标识提供器，并为其配置属性
     *
     * @param context XML节点上下文，用于读取配置信息
     * @throws Exception 如果配置信息有误或初始化提供器时发生错误
     */
    private void databaseIdProviderElement(XNode context) throws Exception {
        // 如果传入的XML节点为空，则直接返回，不进行后续操作
        if (context == null) {
            return;
        }
        // 获取数据库标识提供器的类型
        String type = context.getStringAttribute("type");
        // 为了向后兼容，将"VENDOR"类型更改为"DB_VENDOR"
        // 这是一个为了保持与旧版本兼容的不得已的修补方案
        if ("VENDOR".equals(type)) {
            type = "DB_VENDOR";
        }
        // 将XML节点的子节点解析为属性集合
        Properties properties = context.getChildrenAsProperties();
        // 根据类型解析并实例化对应的数据库标识提供器类
        DatabaseIdProvider databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor()
            .newInstance();
        // 为数据库标识提供器设置属性
        databaseIdProvider.setProperties(properties);
        // 从配置中获取当前环境
        Environment environment = configuration.getEnvironment();
        // 如果环境不为空，则使用数据库标识提供器来获取数据库标识，并设置到配置中
        if (environment != null) {
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }


    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    /**
     * 解析typeHandlers配置元素
     * 该方法负责处理typeHandlers相关的配置，通过解析配置信息来注册相应的类型处理器
     * 类型处理器用于在Java类型和数据库类型之间进行转换
     *
     * @param context 表示typeHandlers配置元素的XNode对象，可能包含多个子元素，每个子元素代表一个类型处理器的配置
     */
    private void typeHandlersElement(XNode context) {
        // 如果context为null，则直接返回，不进行任何处理
        if (context == null) {
            return;
        }
        // 遍历context的所有子元素，每个子元素代表一个类型处理器的配置
        for (XNode child : context.getChildren()) {
            // 如果子元素的名称为"package"，则表示这是一个包名配置，用于批量注册该包下的所有类型处理器
            if ("package".equals(child.getName())) {
                String typeHandlerPackage = child.getStringAttribute("name");
                // 注册包下的所有类型处理器
                typeHandlerRegistry.register(typeHandlerPackage);
            } else {
                // 如果不是"package"配置，则需要单独注册类型处理器
                String javaTypeName = child.getStringAttribute("javaType");
                String jdbcTypeName = child.getStringAttribute("jdbcType");
                String handlerTypeName = child.getStringAttribute("handler");
                // 尝试解析Java类型类
                Class<?> javaTypeClass = resolveClass(javaTypeName);
                // 尝试解析JdbcType枚举值
                JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                // 尝试解析类型处理器类
                Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                // 如果成功解析了Java类型类
                if (javaTypeClass != null) {
                    // 如果没有指定jdbcType，则只注册Java类型类和类型处理器类的映射
                    if (jdbcType == null) {
                        typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                    } else {
                        // 如果指定了jdbcType，则注册Java类型类、jdbcType和类型处理器类的映射
                        typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                    }
                } else {
                    // 如果没有成功解析Java类型类，则仅注册类型处理器类
                    typeHandlerRegistry.register(typeHandlerClass);
                }
            }
        }
    }


    /**
     * 处理mappers元素，根据子元素的不同类型（package、resource、url、class），进行不同的处理。
     * - 对于package元素，将其包含的映射器包添加到配置中。
     * - 对于resource、url元素，加载并解析对应的映射器配置文件。
     * - 对于class元素，将指定的映射器接口类添加到配置中。
     *
     * @param context mappers元素的XNode对象，用于遍历解析子元素。
     * @throws Exception 如果解析过程中发生错误。
     */
    private void mappersElement(XNode context) throws Exception {
        // 如果context为空，则直接返回，不进行处理。
        if (context == null) {
            return;
        }
        // 遍历context的子元素。
        for (XNode child : context.getChildren()) {
            // 如果子元素名称为"package"，则处理映射器包。
            if ("package".equals(child.getName())) {
                // 获取并添加映射器包名。
                String mapperPackage = child.getStringAttribute("name");
                configuration.addMappers(mapperPackage);
            } else {
                // 处理resource、url、class元素。
                String resource = child.getStringAttribute("resource");
                String url = child.getStringAttribute("url");
                String mapperClass = child.getStringAttribute("class");
                // 根据resource、url、mapperClass是否为空，确定处理方式。
                if (resource != null && url == null && mapperClass == null) {
                    // 处理resource元素。
                    ErrorContext.instance().resource(resource);
                    try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                            configuration.getSqlFragments());
                        mapperParser.parse();
                    }
                } else if (resource == null && url != null && mapperClass == null) {
                    // 处理url元素。
                    ErrorContext.instance().resource(url);
                    try (InputStream inputStream = Resources.getUrlAsStream(url)) {
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                            configuration.getSqlFragments());
                        mapperParser.parse();
                    }
                } else if (resource == null && url == null && mapperClass != null) {
                    // 处理class元素。
                    Class<?> mapperInterface = Resources.classForName(mapperClass);
                    configuration.addMapper(mapperInterface);
                } else {
                    // 如果元素同时指定了多个属性，则抛出异常。
                    throw new BuilderException(
                        "A mapper element may only specify a url, resource or class, but not more than one.");
                }
            }
        }
    }


    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        }
        if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        }
        return environment.equals(id);
    }

    /**
     * 创建一个新的Configuration实例
     *
     * @param configClass Configuration类或其子类的Class对象
     * @return 返回一个新的Configuration实例
     * @throws BuilderException 如果实例化失败，则抛出此异常
     */
    private static Configuration newConfig(Class<? extends Configuration> configClass) {
        try {
            // 调用configClass的无参构造方法创建实例
            return configClass.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            // 如果创建实例失败，则抛出BuilderException
            throw new BuilderException("Failed to create a new Configuration instance.", ex);
        }
    }


}
