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
package org.apache.ibatis.datasource.unpooled;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.util.MapUtil;

/**
 * 没有池化的数据库连接，不使用连接池。
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class UnpooledDataSource implements DataSource {
    // JDBC driver
    private ClassLoader driverClassLoader;
    // JDBC driver properties
    private Properties driverProperties;
    // 注册的JDBC driver驱动列表，key是driver的类名
    private static final Map<String, Driver> registeredDrivers = new ConcurrentHashMap<>();

    private String driver;
    private String url;
    private String username;
    private String password;

    private Boolean autoCommit;
    private Integer defaultTransactionIsolationLevel;
    private Integer defaultNetworkTimeout;

    /**
     * 静态初始化块，用于注册所有可用的JDBC驱动。
     * 这段代码在类加载时执行，通过遍历DriverManager获取所有已注册的驱动，
     * 并将它们存储在一个Map中，以便后续使用。
     * 这个做法的目的是为了在不明确知道有哪些驱动的情况下，能够遍历所有驱动，
     * 并提供一个统一的访问方式。
     */
    static {
        // 获取所有已注册的JDBC驱动
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        // 遍历所有驱动，将它们按类名存储在registeredDrivers中
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            registeredDrivers.put(driver.getClass().getName(), driver);
        }
    }


    public UnpooledDataSource() {
    }

    public UnpooledDataSource(String driver, String url, String username, String password) {
        this.driver = driver;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public UnpooledDataSource(String driver, String url, Properties driverProperties) {
        this.driver = driver;
        this.url = url;
        this.driverProperties = driverProperties;
    }

    public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username,
                              String password) {
        this.driverClassLoader = driverClassLoader;
        this.driver = driver;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
        this.driverClassLoader = driverClassLoader;
        this.driver = driver;
        this.url = url;
        this.driverProperties = driverProperties;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return doGetConnection(username, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return doGetConnection(username, password);
    }

    @Override
    public void setLoginTimeout(int loginTimeout) {
        DriverManager.setLoginTimeout(loginTimeout);
    }

    @Override
    public int getLoginTimeout() {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public void setLogWriter(PrintWriter logWriter) {
        DriverManager.setLogWriter(logWriter);
    }

    @Override
    public PrintWriter getLogWriter() {
        return DriverManager.getLogWriter();
    }

    public ClassLoader getDriverClassLoader() {
        return driverClassLoader;
    }

    public void setDriverClassLoader(ClassLoader driverClassLoader) {
        this.driverClassLoader = driverClassLoader;
    }

    public Properties getDriverProperties() {
        return driverProperties;
    }

    public void setDriverProperties(Properties driverProperties) {
        this.driverProperties = driverProperties;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(Boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public Integer getDefaultTransactionIsolationLevel() {
        return defaultTransactionIsolationLevel;
    }

    public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
        this.defaultTransactionIsolationLevel = defaultTransactionIsolationLevel;
    }

    /**
     * Gets the default network timeout.
     *
     * @return the default network timeout
     * @since 3.5.2
     */
    public Integer getDefaultNetworkTimeout() {
        return defaultNetworkTimeout;
    }

    /**
     * Sets the default network timeout value to wait for the database operation to complete. See
     * {@link Connection#setNetworkTimeout(java.util.concurrent.Executor, int)}
     *
     * @param defaultNetworkTimeout The time in milliseconds to wait for the database operation to complete.
     * @since 3.5.2
     */
    public void setDefaultNetworkTimeout(Integer defaultNetworkTimeout) {
        this.defaultNetworkTimeout = defaultNetworkTimeout;
    }

    private Connection doGetConnection(String username, String password) throws SQLException {
        Properties props = new Properties();
        if (driverProperties != null) {
            props.putAll(driverProperties);
        }
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        return doGetConnection(props);
    }

    /**
     * 获取数据库连接。
     * <p>
     * 此方法负责初始化驱动程序，然后使用给定的属性获取数据库连接，
     * 并对连接进行配置，最后返回配置后的连接。
     *
     * @param properties 连接数据库所需的属性，包括用户名和密码等。
     * @return 已配置好的数据库连接。
     * @throws SQLException 如果获取或配置连接过程中发生错误。
     */
    private Connection doGetConnection(Properties properties) throws SQLException {
        // 初始化驱动程序，确保能够与数据库建立连接。
        initializeDriver();
        // 使用驱动程序和属性获取数据库连接。
        Connection connection = DriverManager.getConnection(url, properties);
        // 对获取的连接进行配置，例如设置自动提交等。
        configureConnection(connection);
        // 返回配置后的连接。
        return connection;
    }


    /**
     * 初始化驱动程序。
     * 此方法用于根据配置的驱动类名和可选的驱动类加载器来实例化和注册JDBC驱动程序。
     * 如果已注册的驱动程序列表中不存在当前驱动，则会尝试通过类加载器加载驱动类，
     * 创建驱动实例，并将其注册到DriverManager中。
     *
     * @throws SQLException 如果在初始化或注册驱动程序时出现错误。
     */
    private void initializeDriver() throws SQLException {
        try {
            // 使用computeIfAbsent方法检查已注册的驱动程序列表中是否已存在当前驱动。
            // 如果不存在，则进行实例化和注册。
            MapUtil.computeIfAbsent(registeredDrivers, driver, x -> {
                Class<?> driverType;
                try {
                    // 根据driverClassLoader是否为空，选择不同的方式加载驱动类。
                    if (driverClassLoader != null) {
                        // 使用提供的驱动类加载器加载驱动类。
                        driverType = Class.forName(x, true, driverClassLoader);
                    } else {
                        // 使用Resources的类加载器加载驱动类。
                        driverType = Resources.classForName(x);
                    }
                    // 通过反射创建驱动实例。
                    Driver driverInstance = (Driver) driverType.getDeclaredConstructor().newInstance();
                    // 注册驱动实例，使用DriverProxy封装以提供额外的功能或控制。
                    DriverManager.registerDriver(new DriverProxy(driverInstance));
                    return driverInstance;
                } catch (Exception e) {
                    // 在实例化或注册驱动时发生异常，抛出运行时异常。
                    throw new RuntimeException("Error setting driver on UnpooledDataSource.", e);
                }
            });
        } catch (RuntimeException re) {
            // 捕获运行时异常，并转换为SQLException抛出，以便调用者能更直接地处理JDBC相关的错误。
            throw new SQLException("Error setting driver on UnpooledDataSource.", re.getCause());
        }
    }


    private void configureConnection(Connection conn) throws SQLException {
        if (defaultNetworkTimeout != null) {
            conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), defaultNetworkTimeout);
        }
        if (autoCommit != null && autoCommit != conn.getAutoCommit()) {
            conn.setAutoCommit(autoCommit);
        }
        if (defaultTransactionIsolationLevel != null) {
            conn.setTransactionIsolation(defaultTransactionIsolationLevel);
        }
    }

    private static class DriverProxy implements Driver {
        private final Driver driver;

        DriverProxy(Driver d) {
            this.driver = d;
        }

        @Override
        public boolean acceptsURL(String u) throws SQLException {
            return this.driver.acceptsURL(u);
        }

        @Override
        public Connection connect(String u, Properties p) throws SQLException {
            return this.driver.connect(u, p);
        }

        @Override
        public int getMajorVersion() {
            return this.driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return this.driver.getMinorVersion();
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
            return this.driver.getPropertyInfo(u, p);
        }

        @Override
        public boolean jdbcCompliant() {
            return this.driver.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException(getClass().getName() + " is not a wrapper.");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        // requires JDK version 1.6
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

}
