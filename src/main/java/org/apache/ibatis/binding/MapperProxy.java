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

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.util.MapUtil;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 代理模式，T为mapper接口
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

    private static final long serialVersionUID = -4724728412955527868L;
    // JDK 1.8
    private static final int ALLOWED_MODES = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
        | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;
    // JDK 1.8
    private static final Constructor<Lookup> lookupConstructor;
    // JDK 9
    private static final Method privateLookupInMethod;
    // sqlSession
    private final SqlSession sqlSession;
    // mapper接口
    private final Class<T> mapperInterface;
    // 缓存MapperMethodInvoker
    private final Map<Method, MapperMethodInvoker> methodCache;

    public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethodInvoker> methodCache) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
        this.methodCache = methodCache;
    }

    static {
        Method privateLookupIn;
        try {
            privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
        } catch (NoSuchMethodException e) {
            privateLookupIn = null;
        }
        privateLookupInMethod = privateLookupIn;

        Constructor<Lookup> lookup = null;
        if (privateLookupInMethod == null) {
            // JDK 1.8
            try {
                lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
                lookup.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                    "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
                    e);
            } catch (Exception e) {
                lookup = null;
            }
        }
        lookupConstructor = lookup;
    }

    /**
     * 拦截并处理代理对象上的方法调用。
     * 首先判断待调用的方法是否属于 Object 类。如果是，则直接在当前对象上调用该方法；
     * 否则，使用缓存的方法调用器处理方法调用，并传入代理对象、方法信息、调用参数及 sqlSession。
     * 如果在调用过程中发生任何异常，将捕获并由外层进行处理。
     *
     * @param proxy  要在其上调用方法的代理对象
     * @param method 要调用的方法
     * @param args   方法调用参数
     * @return 方法调用的返回值
     * @throws Throwable 在方法调用期间可能会抛出异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // 检查方法是否属于 Object 类
            if (Object.class.equals(method.getDeclaringClass())) {
                // 直接在当前对象上调用方法
                return method.invoke(this, args);
            }
            // 使用缓存的方法调用器处理方法调用
            return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
        } catch (Throwable t) {
            // 捕获异常并处理
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }


    /**
     * 根据方法获取缓存的Mapper方法调用者
     * 本方法旨在通过方法缓存机制提高方法调用的性能
     * 它首先检查缓存中是否已存在对应方法的调用者，如果不存在，则创建一个新的调用者并缓存起来
     *
     * @param method 要调用的接口方法
     * @return 对应于给定方法的MapperMethodInvoker实例
     * @throws Throwable 包括运行时异常和错误，当方法调用过程中发生错误时抛出
     */
    private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
        try {
            // 使用MapUtil.computeIfAbsent方法根据method生成或获取已缓存的MapperMethodInvoker
            return MapUtil.computeIfAbsent(methodCache, method, m -> {
                // 如果方法不是默认方法，创建并返回一个PlainMethodInvoker
                if (!m.isDefault()) {
                    return new PlainMethodInvoker(new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
                }
                // 对于默认方法，尝试创建DefaultMethodInvoker
                try {
                    // 根据Java版本选择适当的方法句柄获取方式
                    if (privateLookupInMethod == null) {
                        return new DefaultMethodInvoker(getMethodHandleJava8(method));
                    }
                    return new DefaultMethodInvoker(getMethodHandleJava9(method));
                } catch (IllegalAccessException | InstantiationException | InvocationTargetException
                         | NoSuchMethodException e) {
                    // 如果出现异常，将其包装为运行时异常并抛出
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException re) {
            // 处理RuntimeException，获取并可能重新抛出其原因
            Throwable cause = re.getCause();
            throw cause == null ? re : cause;
        }
    }


    /**
     * 从Java 9开始使用MethodHandle
     * 该方法用于获取Method对象的MethodHandle
     * 主要是为了在安全的情况下，提高方法调用的性能
     *
     * @param method Method对象，表示需要获取MethodHandle的方法
     * @return 返回该Method对象的MethodHandle
     * @throws NoSuchMethodException     如果方法不存在
     * @throws IllegalAccessException    如果当前用户没有权限访问method
     * @throws InvocationTargetException 如果方法抛出了异常
     */
    private MethodHandle getMethodHandleJava9(Method method)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // 获取方法所属的声明类
        final Class<?> declaringClass = method.getDeclaringClass();

        // 使用privateLookupInMethod反射调用MethodHandles.privateLookupIn方法
        // 以绕过Java 9引入的访问限制问题
        // 然后使用Lookup对象的findSpecial方法获取特定方法的MethodHandle
        return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup())).findSpecial(
            declaringClass,                    // 方法的声明类
            method.getName(),                 // 方法名
            MethodType.methodType(            // 方法类型，包括返回类型和参数类型
                method.getReturnType(),       // 返回类型
                method.getParameterTypes()),  // 参数类型数组
            declaringClass);                  // 特殊方法的声明类
    }


    /**
     * 在Java 8环境中获取MethodHandle
     * <p>
     * 该方法主要用于在Java 8环境中获取特定方法的MethodHandle对象，
     * 以便支持反射调用。方法通过查找和构造目标类的特殊方法句柄，
     * 允许在给定的访问模式下反射调用特定方法。
     *
     * @param method 要反射调用的目标方法对象
     * @return 返回目标方法的MethodHandle对象
     * @throws IllegalAccessException    当访问非法时抛出异常
     * @throws InstantiationException    当无法创建目标类实例时抛出异常
     * @throws InvocationTargetException 当调用目标方法失败时抛出异常
     */
    private MethodHandle getMethodHandleJava8(Method method)
        throws IllegalAccessException, InstantiationException, InvocationTargetException {
        // 获取目标方法的声明类
        final Class<?> declaringClass = method.getDeclaringClass();
        // 构造并返回目标方法的MethodHandle对象
        return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass);
    }

    interface MapperMethodInvoker {
        Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable;
    }

    /**
     * 简单方法调用者
     */
    private static class PlainMethodInvoker implements MapperMethodInvoker {
        private final MapperMethod mapperMethod;

        public PlainMethodInvoker(MapperMethod mapperMethod) {
            this.mapperMethod = mapperMethod;
        }

        /**
         * 该方法重写自父类，用于处理通过代理对象调用的方法
         * 它的主要作用是将接收到的调用请求转换为MapperMethod的execute方法执行
         *
         * @param proxy      代理对象，通常是一个Mapper接口的代理实例
         * @param method     正在调用的方法的Method对象
         * @param args       方法调用时传递的参数数组
         * @param sqlSession MyBatis的SqlSession对象，用于执行SQL操作
         * @return 该方法调用的结果由MapperMethod的execute方法返回
         * @throws Throwable 如果方法执行过程中发生异常，则抛出Throwable
         *                   该方法需要处理或声明所有可能抛出的异常
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
            // 调用MapperMethod的execute方法执行SQL操作，并返回结果
            return mapperMethod.execute(sqlSession, args);
        }

    }

    /**
     * 默认方法调用者
     */
    private static class DefaultMethodInvoker implements MapperMethodInvoker {
        private final MethodHandle methodHandle;

        public DefaultMethodInvoker(MethodHandle methodHandle) {
            this.methodHandle = methodHandle;
        }

        /**
         * 动态代理调用方法
         *
         * @param proxy      动态代理对象
         * @param method     正在调用的方法
         * @param args       方法参数
         * @param sqlSession SqlSession对象，提供数据库操作上下文
         * @return 调用方法的返回值
         * @throws Throwable 如果方法调用失败，抛出异常
         *                   <p>
         *                   该方法通过MethodHandle（方法句柄）机制绑定代理对象并传递参数，从而实现动态代理的调用功能
         *                   使用方法句柄可以灵活地在运行时反射性地调用目标方法，这在MyBatis等ORM框架中尤为有用
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
            return methodHandle.bindTo(proxy).invokeWithArguments(args);
        }

    }
}
