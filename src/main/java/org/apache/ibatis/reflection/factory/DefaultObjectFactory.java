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
package org.apache.ibatis.reflection.factory;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.Reflector;

/**
 * @author Clinton Begin
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

    private static final long serialVersionUID = -8855120656740914948L;

    @Override
    public <T> T create(Class<T> type) {
        return create(type, null, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        Class<?> classToCreate = resolveInterface(type);
        // 我们知道类型是可分配的
        return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
    }

    /**
     * 根据给定的类类型、构造函数参数类型列表和参数值列表，实例化一个对象。
     * 如果提供了构造函数参数类型和参数值，则尝试使用匹配的构造函数进行实例化；
     * 如果未提供，则尝试使用无参构造函数进行实例化。
     * 对于受保护的或私有的构造函数，会尝试设置其可访问性为true后进行实例化。
     *
     * @param type                待实例化的类的Class对象。
     * @param constructorArgTypes 构造函数的参数类型列表，可以为null。
     * @param constructorArgs     构造函数的参数值列表，可以为null。
     * @return 实例化后的对象。
     * @throws ReflectionException 如果实例化过程中发生错误，会抛出此异常。
     */
    private <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        try {
            Constructor<T> constructor;
            // 检查是否未提供构造函数参数类型和参数值
            if (constructorArgTypes == null || constructorArgs == null) {
                // 尝试获取无参构造函数
                constructor = type.getDeclaredConstructor();
                try {
                    // 尝试使用无参构造函数实例化对象
                    return constructor.newInstance();
                } catch (IllegalAccessException e) {
                    // 如果允许控制成员访问性，则设置构造函数为可访问并重试
                    if (Reflector.canControlMemberAccessible()) {
                        constructor.setAccessible(true);
                        return constructor.newInstance();
                    }
                    throw e;
                }
            }
            // 根据提供的构造函数参数类型列表获取对应的构造函数
            constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[0]));
            try {
                // 尝试使用提供的构造函数参数值列表实例化对象
                return constructor.newInstance(constructorArgs.toArray(new Object[0]));
            } catch (IllegalAccessException e) {
                // 如果允许控制成员访问性，则设置构造函数为可访问并重试
                if (Reflector.canControlMemberAccessible()) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(constructorArgs.toArray(new Object[0]));
                }
                throw e;
            }
        } catch (Exception e) {
            // 构造异常信息，关于实例化失败的错误提示
            String argTypes = Optional.ofNullable(constructorArgTypes).orElseGet(Collections::emptyList).stream()
                .map(Class::getSimpleName).collect(Collectors.joining(","));
            String argValues = Optional.ofNullable(constructorArgs).orElseGet(Collections::emptyList).stream()
                .map(String::valueOf).collect(Collectors.joining(","));
            throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values ("
                + argValues + "). Cause: " + e, e);
        }
    }


    /**
     * 解析给定类型的接口，将其映射到一个具体的实现类。
     * 如果给定类型是集合类（List, Collection, Iterable）之一，则映射到对应的实现类（ArrayList）。
     * 如果是Map类型，则映射到HashMap。
     * 如果是SortedSet类型，则映射到TreeSet。
     * 如果是Set类型，则映射到HashSet。
     * 如果不是这些特定的集合类型，则直接返回原类型。
     *
     * @param type 待解析的类型，通常是接口类型。
     * @return 给定类型的具体实现类。如果无法映射到具体实现，则返回原类型。
     */
    protected Class<?> resolveInterface(Class<?> type) {
        Class<?> classToCreate;
        // 根据给定类型，选择对应的默认实现类
        if (type == List.class || type == Collection.class || type == Iterable.class) {
            classToCreate = ArrayList.class;
        } else if (type == Map.class) {
            classToCreate = HashMap.class;
        } else if (type == SortedSet.class) { // 对应 issue #510 的集合支持
            classToCreate = TreeSet.class;
        } else if (type == Set.class) {
            classToCreate = HashSet.class;
        } else {
            classToCreate = type;
        }
        return classToCreate;
    }


    @Override
    public <T> boolean isCollection(Class<T> type) {
        return Collection.class.isAssignableFrom(type);
    }

}
