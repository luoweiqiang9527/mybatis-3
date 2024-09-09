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

import static com.googlecode.catchexception.apis.BDDCatchException.caughtException;
import static com.googlecode.catchexception.apis.BDDCatchException.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

import org.apache.ibatis.builder.mapper.CustomMapper;
import org.apache.ibatis.builder.typehandler.CustomIntegerTypeHandler;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.domain.blog.Author;
import org.apache.ibatis.domain.blog.Blog;
import org.apache.ibatis.domain.blog.mappers.BlogMapper;
import org.apache.ibatis.domain.blog.mappers.NestedBlogMapper;
import org.apache.ibatis.domain.jpetstore.Cart;
import org.apache.ibatis.executor.loader.cglib.CglibProxyFactory;
import org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory;
import org.apache.ibatis.io.JBoss6VFS;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.EnumOrdinalTypeHandler;
import org.apache.ibatis.type.EnumTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class XmlConfigBuilderTest {

    /**
     * 测试加载最小XML配置文件的功能
     * 此方法验证了XML配置文件是否能被正确解析，并检查了配置对象中的各种设置是否符合预期
     * 验证的设置包括自动映射行为、缓存启用状态、代理工厂类型、懒加载设置、结果集和列标签使用等
     * 该测试用例确保了MyBatis配置文件的解析功能正常工作，并且默认配置符合预期的行为
     *
     * @throws Exception 如果配置文件解析失败或配置项不符合预期
     */
    @Test
    void shouldSuccessfullyLoadMinimalXMLConfigFile() throws Exception {
        // 定义资源路径
        String resource = "org/apache/ibatis/builder/MinimalMapperConfig.xml";
        // 使用try-with-resources确保输入流在使用后正确关闭
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            // 创建XML配置构建器并传递输入流
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream);
            // 解析XML配置并获取配置对象
            Configuration config = builder.parse();
            // 确认配置对象不为空
            assertNotNull(config);
            // 检查自动映射行为是否为PARTIAL
            assertThat(config.getAutoMappingBehavior()).isEqualTo(AutoMappingBehavior.PARTIAL);
            // 检查未知列行为是否为NONE
            assertThat(config.getAutoMappingUnknownColumnBehavior()).isEqualTo(AutoMappingUnknownColumnBehavior.NONE);
            // 检查缓存是否已启用
            assertThat(config.isCacheEnabled()).isTrue();
            // 检查代理工厂是否为JavassistProxyFactory实例
            assertThat(config.getProxyFactory()).isInstanceOf(JavassistProxyFactory.class);
            // 检查懒加载是否已禁用
            assertThat(config.isLazyLoadingEnabled()).isFalse();
            // 检查是否启用激进的懒加载
            assertThat(config.isAggressiveLazyLoading()).isFalse();
            // 检查是否支持多个结果集
            assertThat(config.isMultipleResultSetsEnabled()).isTrue();
            // 检查是否使用列标签
            assertThat(config.isUseColumnLabel()).isTrue();
            // 检查是否使用生成的键
            assertThat(config.isUseGeneratedKeys()).isFalse();
            // 检查默认执行器类型是否为SIMPLE
            assertThat(config.getDefaultExecutorType()).isEqualTo(ExecutorType.SIMPLE);
            // 检查默认语句超时是否未设置
            assertNull(config.getDefaultStatementTimeout());
            // 检查默认获取大小是否未设置
            assertNull(config.getDefaultFetchSize());
            // 检查默认结果集类型是否未设置
            assertNull(config.getDefaultResultSetType());
            // 检查是否将下划线映射到驼峰命名
            assertThat(config.isMapUnderscoreToCamelCase()).isFalse();
            // 检查是否启用了安全的行界限
            assertThat(config.isSafeRowBoundsEnabled()).isFalse();
            // 检查本地缓存范围是否为SESSION
            assertThat(config.getLocalCacheScope()).isEqualTo(LocalCacheScope.SESSION);
            // 检查为null值设置的JDBC类型
            assertThat(config.getJdbcTypeForNull()).isEqualTo(JdbcType.OTHER);
            // 检查懒加载触发方法是否包括equals、clone、hashCode和toString
            assertThat(config.getLazyLoadTriggerMethods())
                .isEqualTo(new HashSet<>(Arrays.asList("equals", "clone", "hashCode", "toString")));
            // 检查是否启用了安全的结果处理器
            assertThat(config.isSafeResultHandlerEnabled()).isTrue();
            // 检查默认脚本语言驱动是否为XMLLanguageDriver实例
            assertThat(config.getDefaultScriptingLanuageInstance()).isInstanceOf(XMLLanguageDriver.class);
            // 检查是否在nulls上调用设置器
            assertThat(config.isCallSettersOnNulls()).isFalse();
            // 检查日志前缀是否未设置
            assertNull(config.getLogPrefix());
            // 检查日志实现是否未设置
            assertNull(config.getLogImpl());
            // 检查配置工厂是否未设置
            assertNull(config.getConfigurationFactory());
            // 检查RoundingMode类的类型处理器是否为EnumTypeHandler实例
            assertThat(config.getTypeHandlerRegistry().getTypeHandler(RoundingMode.class))
                .isInstanceOf(EnumTypeHandler.class);
            // 检查是否在SQL中缩小空格
            assertThat(config.isShrinkWhitespacesInSql()).isFalse();
            // 检查是否启用了基于名称的构造函数自动映射
            assertThat(config.isArgNameBasedConstructorAutoMapping()).isFalse();
            // 检查默认SQL提供者类型是否未设置
            assertThat(config.getDefaultSqlProviderType()).isNull();
            // 检查是否在ForEach中启用可空值
            assertThat(config.isNullableOnForEach()).isFalse();
        }
    }


    enum MyEnum {

        ONE,

        TWO

    }

    public static class EnumOrderTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

        private final E[] constants;

        public EnumOrderTypeHandler(Class<E> javaType) {
            constants = javaType.getEnumConstants();
        }

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
            ps.setInt(i, parameter.ordinal() + 1); // 0 means NULL so add +1
        }

        @Override
        public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int index = rs.getInt(columnName) - 1;
            return index < 0 ? null : constants[index];
        }

        @Override
        public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int index = rs.getInt(rs.getInt(columnIndex)) - 1;
            return index < 0 ? null : constants[index];
        }

        @Override
        public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int index = cs.getInt(columnIndex) - 1;
            return index < 0 ? null : constants[index];
        }
    }

    @Test
    void registerJavaTypeInitializingTypeHandler() {
        // @formatter:off
    final String MAPPER_CONFIG = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
        + "<!DOCTYPE configuration PUBLIC \"-//mybatis.org//DTD Config 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-config.dtd\">\n"
        + "<configuration>\n"
        + "  <typeHandlers>\n"
        + "    <typeHandler javaType=\"org.apache.ibatis.builder.XmlConfigBuilderTest$MyEnum\"\n"
        + "      handler=\"org.apache.ibatis.builder.XmlConfigBuilderTest$EnumOrderTypeHandler\"/>\n"
        + "  </typeHandlers>\n"
        + "</configuration>\n";
    // @formatter:on

        XMLConfigBuilder builder = new XMLConfigBuilder(new StringReader(MAPPER_CONFIG));
        builder.parse();

        TypeHandlerRegistry typeHandlerRegistry = builder.getConfiguration().getTypeHandlerRegistry();
        TypeHandler<MyEnum> typeHandler = typeHandlerRegistry.getTypeHandler(MyEnum.class);

        assertTrue(typeHandler instanceof EnumOrderTypeHandler);
        assertArrayEquals(MyEnum.values(), ((EnumOrderTypeHandler<MyEnum>) typeHandler).constants);
    }

    @Tag("RequireIllegalAccess")
    @Test
    void shouldSuccessfullyLoadXMLConfigFile() throws Exception {
        String resource = "org/apache/ibatis/builder/CustomizedSettingsMapperConfig.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            Properties props = new Properties();
            props.put("prop2", "cccc");
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream, null, props);
            Configuration config = builder.parse();

            assertThat(config.getAutoMappingBehavior()).isEqualTo(AutoMappingBehavior.NONE);
            assertThat(config.getAutoMappingUnknownColumnBehavior()).isEqualTo(AutoMappingUnknownColumnBehavior.WARNING);
            assertThat(config.isCacheEnabled()).isFalse();
            assertThat(config.getProxyFactory()).isInstanceOf(CglibProxyFactory.class);
            assertThat(config.isLazyLoadingEnabled()).isTrue();
            assertThat(config.isAggressiveLazyLoading()).isTrue();
            assertThat(config.isMultipleResultSetsEnabled()).isFalse();
            assertThat(config.isUseColumnLabel()).isFalse();
            assertThat(config.isUseGeneratedKeys()).isTrue();
            assertThat(config.getDefaultExecutorType()).isEqualTo(ExecutorType.BATCH);
            assertThat(config.getDefaultStatementTimeout()).isEqualTo(10);
            assertThat(config.getDefaultFetchSize()).isEqualTo(100);
            assertThat(config.getDefaultResultSetType()).isEqualTo(ResultSetType.SCROLL_INSENSITIVE);
            assertThat(config.isMapUnderscoreToCamelCase()).isTrue();
            assertThat(config.isSafeRowBoundsEnabled()).isTrue();
            assertThat(config.getLocalCacheScope()).isEqualTo(LocalCacheScope.STATEMENT);
            assertThat(config.getJdbcTypeForNull()).isEqualTo(JdbcType.NULL);
            assertThat(config.getLazyLoadTriggerMethods())
                .isEqualTo(new HashSet<>(Arrays.asList("equals", "clone", "hashCode", "toString", "xxx")));
            assertThat(config.isSafeResultHandlerEnabled()).isFalse();
            assertThat(config.getDefaultScriptingLanuageInstance()).isInstanceOf(RawLanguageDriver.class);
            assertThat(config.isCallSettersOnNulls()).isTrue();
            assertThat(config.getLogPrefix()).isEqualTo("mybatis_");
            assertThat(config.getLogImpl().getName()).isEqualTo(Slf4jImpl.class.getName());
            assertThat(config.getVfsImpl().getName()).isEqualTo(JBoss6VFS.class.getName());
            assertThat(config.getConfigurationFactory().getName()).isEqualTo(String.class.getName());
            assertThat(config.isShrinkWhitespacesInSql()).isTrue();
            assertThat(config.isArgNameBasedConstructorAutoMapping()).isTrue();
            assertThat(config.getDefaultSqlProviderType().getName()).isEqualTo(MySqlProvider.class.getName());
            assertThat(config.isNullableOnForEach()).isTrue();

            assertThat(config.getTypeAliasRegistry().getTypeAliases().get("blogauthor")).isEqualTo(Author.class);
            assertThat(config.getTypeAliasRegistry().getTypeAliases().get("blog")).isEqualTo(Blog.class);
            assertThat(config.getTypeAliasRegistry().getTypeAliases().get("cart")).isEqualTo(Cart.class);

            assertThat(config.getTypeHandlerRegistry().getTypeHandler(Integer.class))
                .isInstanceOf(CustomIntegerTypeHandler.class);
            assertThat(config.getTypeHandlerRegistry().getTypeHandler(Long.class)).isInstanceOf(CustomLongTypeHandler.class);
            assertThat(config.getTypeHandlerRegistry().getTypeHandler(String.class))
                .isInstanceOf(CustomStringTypeHandler.class);
            assertThat(config.getTypeHandlerRegistry().getTypeHandler(String.class, JdbcType.VARCHAR))
                .isInstanceOf(CustomStringTypeHandler.class);
            assertThat(config.getTypeHandlerRegistry().getTypeHandler(RoundingMode.class))
                .isInstanceOf(EnumOrdinalTypeHandler.class);

            ExampleObjectFactory objectFactory = (ExampleObjectFactory) config.getObjectFactory();
            assertThat(objectFactory.getProperties().size()).isEqualTo(1);
            assertThat(objectFactory.getProperties().getProperty("objectFactoryProperty")).isEqualTo("100");

            assertThat(config.getObjectWrapperFactory()).isInstanceOf(CustomObjectWrapperFactory.class);

            assertThat(config.getReflectorFactory()).isInstanceOf(CustomReflectorFactory.class);

            ExamplePlugin plugin = (ExamplePlugin) config.getInterceptors().get(0);
            assertThat(plugin.getProperties().size()).isEqualTo(1);
            assertThat(plugin.getProperties().getProperty("pluginProperty")).isEqualTo("100");

            Environment environment = config.getEnvironment();
            assertThat(environment.getId()).isEqualTo("development");
            assertThat(environment.getDataSource()).isInstanceOf(UnpooledDataSource.class);
            assertThat(environment.getTransactionFactory()).isInstanceOf(JdbcTransactionFactory.class);

            assertThat(config.getDatabaseId()).isEqualTo("derby");

            assertThat(config.getMapperRegistry().getMappers().size()).isEqualTo(4);
            assertThat(config.getMapperRegistry().hasMapper(CachedAuthorMapper.class)).isTrue();
            assertThat(config.getMapperRegistry().hasMapper(CustomMapper.class)).isTrue();
            assertThat(config.getMapperRegistry().hasMapper(BlogMapper.class)).isTrue();
            assertThat(config.getMapperRegistry().hasMapper(NestedBlogMapper.class)).isTrue();
        }
    }

    @Test
    void shouldSuccessfullyLoadXMLConfigFileWithPropertiesUrl() throws Exception {
        String resource = "org/apache/ibatis/builder/PropertiesUrlMapperConfig.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream);
            Configuration config = builder.parse();
            assertThat(config.getVariables().get("driver").toString()).isEqualTo("org.apache.derby.jdbc.EmbeddedDriver");
            assertThat(config.getVariables().get("prop1").toString()).isEqualTo("bbbb");
        }
    }

    @Test
    void parseIsTwice() throws Exception {
        String resource = "org/apache/ibatis/builder/MinimalMapperConfig.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLConfigBuilder builder = new XMLConfigBuilder(inputStream);
            builder.parse();

            when(builder::parse);
            then(caughtException()).isInstanceOf(BuilderException.class)
                .hasMessage("Each XMLConfigBuilder can only be used once.");
        }
    }

    @Test
    void unknownSettings() {
        // @formatter:off
    final String MAPPER_CONFIG = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE configuration PUBLIC \"-//mybatis.org//DTD Config 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-config.dtd\">\n"
            + "<configuration>\n"
            + "  <settings>\n"
            + "    <setting name=\"foo\" value=\"bar\"/>\n"
            + "  </settings>\n"
            + "</configuration>\n";
    // @formatter:on

        XMLConfigBuilder builder = new XMLConfigBuilder(new StringReader(MAPPER_CONFIG));
        when(builder::parse);
        then(caughtException()).isInstanceOf(BuilderException.class)
            .hasMessageContaining("The setting foo is not known.  Make sure you spelled it correctly (case sensitive).");
    }

    @Test
    void unknownJavaTypeOnTypeHandler() {
        // @formatter:off
    final String MAPPER_CONFIG = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE configuration PUBLIC \"-//mybatis.org//DTD Config 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-config.dtd\">\n"
            + "<configuration>\n"
            + "  <typeAliases>\n"
            + "    <typeAlias type=\"a.b.c.Foo\"/>\n"
            + "  </typeAliases>\n"
            + "</configuration>\n";
    // @formatter:on

        XMLConfigBuilder builder = new XMLConfigBuilder(new StringReader(MAPPER_CONFIG));
        when(builder::parse);
        then(caughtException()).isInstanceOf(BuilderException.class)
            .hasMessageContaining("Error registering typeAlias for 'null'. Cause: ");
    }

    @Test
    void propertiesSpecifyResourceAndUrlAtSameTime() {
        // @formatter:off
    final String MAPPER_CONFIG = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
        + "<!DOCTYPE configuration PUBLIC \"-//mybatis.org//DTD Config 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-config.dtd\">\n"
        + "<configuration>\n"
        + "  <properties resource=\"a/b/c/foo.properties\" url=\"file:./a/b/c/jdbc.properties\"/>\n"
        + "</configuration>\n";
    // @formatter:on

        XMLConfigBuilder builder = new XMLConfigBuilder(new StringReader(MAPPER_CONFIG));
        when(builder::parse);
        then(caughtException()).isInstanceOf(BuilderException.class).hasMessageContaining(
            "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
    }

    static class MySqlProvider {
        @SuppressWarnings("unused")
        public static String provideSql() {
            return "SELECT 1";
        }

        private MySqlProvider() {
        }
    }

    /**
     * 测试允许子类配置的解析
     * 该测试用例旨在验证XMLConfigBuilder是否允许通过子类配置来扩展默认配置
     *
     * @throws IOException 如果在读取或解析配置文件时发生错误
     */
    @Test
    void shouldAllowSubclassedConfiguration() throws IOException {
        // 定义配置文件的路径
        String resource = "org/apache/ibatis/builder/MinimalMapperConfig.xml";
        // 使用try-with-resources确保文件资源的自动关闭
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            // 创建一个XMLConfigBuilder实例，传入子类配置和输入流
            XMLConfigBuilder builder = new XMLConfigBuilder(MyConfiguration.class, inputStream, null, null);
            // 解析配置文件并获取配置实例
            Configuration config = builder.parse();

            // 验证返回的配置实例是否是预期的子类类型
            assertThat(config).isInstanceOf(MyConfiguration.class);
        }
    }


    /**
     * 测试子类配置没有默认构造函数时的情况
     * 该测试用例验证了当尝试为没有默认构造函数的子类配置创建 Configuration 实例时，是否正确抛出了异常
     *
     * @throws IOException 如果处理输入流时发生错误
     */
    @Test
    void noDefaultConstructorForSubclassedConfiguration() throws IOException {
        // 定义要读取的资源文件路径
        String resource = "org/apache/ibatis/builder/MinimalMapperConfig.xml";
        // 使用 try-with-resources 确保资源文件的输入流在使用后能被正确关闭
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            // 针对 BadConfiguration 类和给定的输入流，预期构造 XMLConfigBuilder 时抛出异常
            Exception exception = Assertions.assertThrows(Exception.class,
                () -> new XMLConfigBuilder(BadConfiguration.class, inputStream, null, null));
            // 验证抛出的异常消息是否与预期相符
            assertEquals("Failed to create a new Configuration instance.", exception.getMessage());
        }
    }

    public static class MyConfiguration extends Configuration {
        // only using to check configuration was used
    }

    public static class BadConfiguration extends Configuration {

        public BadConfiguration(String parameter) {
            // should have a default constructor
        }
    }

}
