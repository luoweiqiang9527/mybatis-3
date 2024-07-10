/*
 *    Copyright 2009-2022 the original author or authors.
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

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.DataSourceException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * @author Clinton Begin
 */
public class UnpooledDataSourceFactory implements DataSourceFactory {
    // JDBC驱动的属性前缀
    private static final String DRIVER_PROPERTY_PREFIX = "driver.";
    // JDBC驱动的属性前缀长度
    private static final int DRIVER_PROPERTY_PREFIX_LENGTH = DRIVER_PROPERTY_PREFIX.length();
    // 数据源
    protected DataSource dataSource;

    public UnpooledDataSourceFactory() {
        // 创建新的数据源
        this.dataSource = new UnpooledDataSource();
    }

    /**
     * 设置数据源的属性。
     * 该方法用于接收一个属性集合并将它们应用于数据源。属性分为两类：驱动器属性和数据源特定属性。
     * 驱动器属性以特定前缀标识，其余属性被视为数据源特定属性。驱动器属性被剥离前缀后用于配置驱动器，
     * 而数据源特定属性则直接应用于数据源对象。
     *
     * @param properties 属性集合，包含要设置的驱动器和数据源属性。
     */
    @Override
    public void setProperties(Properties properties) {
        // 初始化一个用于存储驱动器属性的容器
        Properties driverProperties = new Properties();
        // 使用SystemMetaObject为dataSource对象创建一个元对象，用于反射式设置属性
        MetaObject metaDataSource = SystemMetaObject.forObject(dataSource);
        // 遍历传入的属性集合
        for (Object key : properties.keySet()) {
            String propertyName = (String) key;
            // 判断当前属性是否为驱动器属性
            if (propertyName.startsWith(DRIVER_PROPERTY_PREFIX)) {
                // 获取属性值，并将其前缀剥离后设置到驱动器属性容器中
                String value = properties.getProperty(propertyName);
                // 去除掉驱动器属性前缀
                driverProperties.setProperty(propertyName.substring(DRIVER_PROPERTY_PREFIX_LENGTH), value);
            } else if (metaDataSource.hasSetter(propertyName)) {
                // 如果当前属性是数据源对象的已知属性，获取其值并进行类型转换后设置到数据源对象上
                String value = (String) properties.get(propertyName);
                Object convertedValue = convertValue(metaDataSource, propertyName, value);
                metaDataSource.setValue(propertyName, convertedValue);
            } else {
                // 如果当前属性既不是驱动器属性也不是数据源对象的已知属性，则抛出异常
                throw new DataSourceException("Unknown DataSource property: " + propertyName);
            }
        }
        // 如果驱动器属性容器不为空，则将其设置到数据源对象的driverProperties属性上
        if (!driverProperties.isEmpty()) {
            metaDataSource.setValue("driverProperties", driverProperties);
        }
    }


    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 根据属性类型将字符串值转换为相应的数据类型。
     *
     * @param metaDataSource 元数据源，用于获取属性的设置器类型。
     * @param propertyName 属性名，用于从元数据源中获取属性的设置器类型。
     * @param value 需要转换的字符串值。
     * @return 转换后的值，其类型匹配属性的设置器类型。
     *
     * 此方法用于在将值设置到对象的属性之前，根据属性的类型将字符串值转换为适当的类型。
     * 它支持 Integer、Long、Boolean 类型的转换，对于其他类型，它将字符串值本身作为结果返回。
     */
    private Object convertValue(MetaObject metaDataSource, String propertyName, String value) {
        // 初始化转换后的值为原始字符串值
        Object convertedValue = value;
        // 获取属性的设置器类型
        Class<?> targetType = metaDataSource.getSetterType(propertyName);

        // 根据目标类型将字符串值转换为相应的数值类型或布尔类型
        if (targetType == Integer.class || targetType == int.class) {
            convertedValue = Integer.valueOf(value);
        } else if (targetType == Long.class || targetType == long.class) {
            convertedValue = Long.valueOf(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            convertedValue = Boolean.valueOf(value);
        }

        // 返回转换后的值
        return convertedValue;
    }


}
