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
package org.apache.ibatis.binding;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {

    private final Configuration config;
    // mapper接口与代理工厂的映射
    private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new ConcurrentHashMap<>();

    public MapperRegistry(Configuration config) {
        this.config = config;
    }

    /**
     * 根据给定的类型和SqlSession对象获取Mapper实例
     *
     * @param type Mapper接口的类型
     * @param sqlSession SqlSession对象，用于执行SQL操作
     * @return Mapper接口的代理实例
     * @throws BindingException 如果类型未在MapperRegistry中注册，或者实例化Mapper代理时发生错误
     */
    @SuppressWarnings("unchecked")
    public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
        // 从已知的Mapper类型中获取相应的MapperProxyFactory
        final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
        if (mapperProxyFactory == null) {
            // 如果未找到对应的MapperProxyFactory，抛出异常
            throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
        }
        try {
            // 使用MapperProxyFactory创建Mapper接口的代理实例
            return mapperProxyFactory.newInstance(sqlSession);
        } catch (Exception e) {
            // 如果实例化过程中发生异常，抛出BindingException并携带异常信息
            throw new BindingException("Error getting mapper instance. Cause: " + e, e);
        }
    }

    public <T> boolean hasMapper(Class<T> type) {
        return knownMappers.containsKey(type);
    }

    /**
     * 向Mapper注册表添加Mapper接口
     *
     * @param type 要添加的Mapper接口的Class类型
     * @throws BindingException 如果指定的类型已经存在于Mapper注册表中
     *                          <p>
     *                          此方法的作用是将一个接口类型添加到Mapper注册表中，以便后续可以使用该接口来生成Mapper对象
     *                          它首先检查指定类型是否为接口，并且是否已经存在于注册表中如果已经存在，则抛出BindingException
     *                          如果不存在，则尝试将该类型添加到注册表中，并通过MapperAnnotationBuilder解析注解来配置映射关系
     *                          如果解析过程中发生异常，则该类型会被从注册表中移除以保持一致性
     */
    public <T> void addMapper(Class<T> type) {
        // 检查指定类型是否为接口
        if (type.isInterface()) {
            // 检查该类型是否已经存在于注册表中
            if (hasMapper(type)) {
                throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
            }
            boolean loadCompleted = false;
            try {
                // 将类型及其对应的MapperProxyFactory添加到注册表中
                knownMappers.put(type, new MapperProxyFactory<>(type));
                // 重要：在解析器运行之前添加类型
                // 这样可以防止在解析Mapper注解时自动尝试绑定
                // 如果类型已经已知，解析器将不会尝试绑定
                MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
                parser.parse();
                loadCompleted = true;
            } finally {
                // 如果加载未完成，则从注册表中移除该类型，保持事务一致性
                if (!loadCompleted) {
                    knownMappers.remove(type);
                }
            }
        }
    }

    /**
     * Gets the mappers.
     *
     * @return the mappers
     * @since 3.2.2
     */
    public Collection<Class<?>> getMappers() {
        return Collections.unmodifiableCollection(knownMappers.keySet());
    }

    /**
     * 添加映射器。
     *
     * @param packageName 包名
     * @param superType   超类型
     * @since 3.2.2
     */
    public void addMappers(String packageName, Class<?> superType) {
        // 创建一个 ResolverUtil 实例来发现类
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
        // 查找指定包下继承自 superType 的所有类
        resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
        // 获取找到的映射器类集合
        Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
        // 遍历映射器类集合，添加每个映射器类
        for (Class<?> mapperClass : mapperSet) {
            addMapper(mapperClass);
        }
    }


    /**
     * 添加映射器。
     *
     * @param packageName 包名称
     * @since 3.2.2
     */
    public void addMappers(String packageName) {
        addMappers(packageName, Object.class);
    }


}
