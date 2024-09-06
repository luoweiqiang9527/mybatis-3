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
package org.apache.ibatis.binding;

import org.apache.ibatis.binding.MapperProxy.MapperMethodInvoker;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Lasse Voss
 */
public class MapperProxyFactory<T> {

    private final Class<T> mapperInterface;
    private final Map<Method, MapperMethodInvoker> methodCache = new ConcurrentHashMap<>();

    public MapperProxyFactory(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    public Class<T> getMapperInterface() {
        return mapperInterface;
    }

    public Map<Method, MapperMethodInvoker> getMethodCache() {
        return methodCache;
    }

    /**
     * 根据给定的MapperProxy实例创建一个新的实例对象
     * 该方法使用Java动态代理来创建一个具有指定接口类型的新实例，并将mapperProxy作为调用处理程序传递
     * 通过这种方式，可以动态生成具有特定行为的代理类实例
     *
     * @param mapperProxy MapperProxy实例，用于处理生成的代理实例的方法调用
     * @return 返回一个具有指定类型T的新代理实例，该实例的方法调用将由mapperProxy处理
     */
    @SuppressWarnings("unchecked")
    protected T newInstance(MapperProxy<T> mapperProxy) {
        // 使用Proxy.newProxyInstance创建一个新的代理实例
        // 第一个参数是接口的类加载器，第二个参数是代理类要实现的接口列表，第三个参数是调用处理程序
        // 这里将mapperProxy作为调用处理程序传递，以便代理实例的方法调用可以由mapperProxy处理
        return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[]{mapperInterface}, mapperProxy);
    }


    /**
     * 根据提供的SqlSession创建Mapper接口的实例
     * 该方法主要用于在Mapper接口的实例化过程中，注入SqlSession，以便Mapper能够执行数据库操作
     *
     * @param sqlSession SqlSession实例，用于执行SQL操作的核心对象
     * @return 返回实现了mapperInterface接口的代理对象
     */
    public T newInstance(SqlSession sqlSession) {
        // 创建MapperProxy实例，传入SqlSession、mapper接口和方法缓存
        final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
        // 调用newInstance方法，传入mapperProxy，返回实现接口的代理对象
        return newInstance(mapperProxy);
    }

}
