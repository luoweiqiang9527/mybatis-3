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
package org.apache.ibatis.mapping;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.cache.decorators.BlockingCache;
import org.apache.ibatis.cache.decorators.LoggingCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.ScheduledCache;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.decorators.SynchronizedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * @author Clinton Begin
 */
public class CacheBuilder {
    private final String id;
    private Class<? extends Cache> implementation;
    private final List<Class<? extends Cache>> decorators;
    private Integer size;
    private Long clearInterval;
    private boolean readWrite;
    private Properties properties;
    private boolean blocking;

    public CacheBuilder(String id) {
        this.id = id;
        this.decorators = new ArrayList<>();
    }

    public CacheBuilder implementation(Class<? extends Cache> implementation) {
        this.implementation = implementation;
        return this;
    }

    /**
     * 添加缓存装饰器类
     *
     * @param decorator 缓存装饰器类，继承自Cache接口
     * @return 返回当前的CacheBuilder实例，以支持链式调用
     */
    public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
        if (decorator != null) {
            this.decorators.add(decorator);
        }
        return this;
    }


    public CacheBuilder size(Integer size) {
        this.size = size;
        return this;
    }

    public CacheBuilder clearInterval(Long clearInterval) {
        this.clearInterval = clearInterval;
        return this;
    }

    public CacheBuilder readWrite(boolean readWrite) {
        this.readWrite = readWrite;
        return this;
    }

    public CacheBuilder blocking(boolean blocking) {
        this.blocking = blocking;
        return this;
    }

    public CacheBuilder properties(Properties properties) {
        this.properties = properties;
        return this;
    }

    /**
     * 构建缓存实例的核心方法
     * 此方法负责创建基础缓存实例，应用默认实现，设置缓存属性，
     * 以及根据需要应用装饰器
     *
     * @return Cache 返回完全配置的缓存实例
     */
    public Cache build() {
        // 设置默认的缓存实现
        setDefaultImplementations();
        // 创建基础缓存实例
        Cache cache = newBaseCacheInstance(implementation, id);
        // 设置缓存属性
        setCacheProperties(cache);

        // 根据缓存类型决定是否应用装饰器
        // 对于基础缓存（PerpetualCache），应用标准装饰器
        if (PerpetualCache.class.equals(cache.getClass())) {
            // 为缓存实例应用所有指定的装饰器
            for (Class<? extends Cache> decorator : decorators) {
                cache = newCacheDecoratorInstance(decorator, cache);
                setCacheProperties(cache);
            }
            // 应用标准装饰器
            cache = setStandardDecorators(cache);
        } else {
            // 对于非日志缓存，添加日志装饰器
            if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
                cache = new LoggingCache(cache);
            }
        }
        // 返回完全配置的缓存实例
        return cache;
    }

    /**
     * 设置默认的实现类和装饰器此类旨在为某些功能提供默认的实现方式，
     * 主要用于缓存机制当没有显式指定实现类时，使用默认的PerpetualCache类
     * 此外，如果装饰器列表为空，则添加LruCache作为默认装饰器
     * 这是为了确保缓存操作在没有特别指定的情况下，也能高效地运行
     */
    private void setDefaultImplementations() {
        // 当实现类还未被设置时，将其设置为默认的PerpetualCache类
        if (implementation == null) {
            implementation = PerpetualCache.class;
            // 如果装饰器列表为空，添加LruCache作为默认装饰器
            if (decorators.isEmpty()) {
                decorators.add(LruCache.class);
            }
        }
    }


    /**
     * 设置标准的缓存装饰器
     * 本方法用于配置和返回一个经过一系列预设装饰器装饰的缓存对象通过这种方式，
     * 可以为缓存对象添加通用的功能，如大小限制、定时清理、序列化、日志记录、同步和阻塞等
     *
     * @param cache 原始缓存对象，将被装饰器装饰
     * @return 装饰后的缓存对象，具有配置好的特性
     * @throws CacheException 如果在构建缓存装饰器过程中发生错误，将抛出此异常
     */
    private Cache setStandardDecorators(Cache cache) {
        try {
            // 获取缓存对象的元对象，用于反射调用
            MetaObject metaCache = SystemMetaObject.forObject(cache);

            // 如果size不为空且缓存对象有对应的setter方法，则设置size属性
            if (size != null && metaCache.hasSetter("size")) {
                metaCache.setValue("size", size);
            }

            // 如果clearInterval不为空，则用定时清理缓存包装原始缓存，并设置清理间隔
            if (clearInterval != null) {
                cache = new ScheduledCache(cache);
                ((ScheduledCache) cache).setClearInterval(clearInterval);
            }

            // 如果readWrite为true，则用序列化缓存包装原始缓存，支持读写操作
            if (readWrite) {
                cache = new SerializedCache(cache);
            }

            // 用日志记录缓存包装原始缓存，增加日志功能
            cache = new LoggingCache(cache);

            // 用同步缓存包装原始缓存，确保缓存操作的线程安全
            cache = new SynchronizedCache(cache);

            // 如果blocking为true，则用阻塞缓存包装原始缓存，支持阻塞操作
            if (blocking) {
                cache = new BlockingCache(cache);
            }

            // 返回经过装饰的缓存对象
            return cache;
        } catch (Exception e) {
            // 如果发生异常，则抛出CacheException，表明构建标准缓存装饰器时出错
            throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
        }
    }

    /**
     * 设置缓存属性
     *
     * @param cache 缓存实例，将为其设置属性
     * @throws CacheException 如果属性类型不支持或缓存初始化失败
     */
    private void setCacheProperties(Cache cache) {
        // 如果属性不为空
        if (properties != null) {
            // 获取缓存对象的元对象，用于操作对象的属性
            MetaObject metaCache = SystemMetaObject.forObject(cache);
            // 遍历属性集合
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                // 获取属性名和值
                String name = (String) entry.getKey();
                String value = (String) entry.getValue();
                // 如果缓存对象有对应的setter方法
                if (metaCache.hasSetter(name)) {
                    // 获取该属性的类型
                    Class<?> type = metaCache.getSetterType(name);
                    // 根据类型转换值并设置属性
                    if (String.class == type) {
                        metaCache.setValue(name, value);
                    } else if (int.class == type || Integer.class == type) {
                        metaCache.setValue(name, Integer.valueOf(value));
                    } else if (long.class == type || Long.class == type) {
                        metaCache.setValue(name, Long.valueOf(value));
                    } else if (short.class == type || Short.class == type) {
                        metaCache.setValue(name, Short.valueOf(value));
                    } else if (byte.class == type || Byte.class == type) {
                        metaCache.setValue(name, Byte.valueOf(value));
                    } else if (float.class == type || Float.class == type) {
                        metaCache.setValue(name, Float.valueOf(value));
                    } else if (boolean.class == type || Boolean.class == type) {
                        metaCache.setValue(name, Boolean.valueOf(value));
                    } else if (double.class == type || Double.class == type) {
                        metaCache.setValue(name, Double.valueOf(value));
                    } else {
                        // 如果类型不支持，抛出异常
                        throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
                    }
                }
            }
        }
        // 如果缓存实现类实现了InitializingObject接口，调用其初始化方法
        if (InitializingObject.class.isAssignableFrom(cache.getClass())) {
            try {
                ((InitializingObject) cache).initialize();
            } catch (Exception e) {
                // 如果初始化失败，抛出缓存异常
                throw new CacheException(
                    "Failed cache initialization for '" + cache.getId() + "' on '" + cache.getClass().getName() + "'", e);
            }
        }
    }


    /**
     * 创建一个新的基础缓存实例
     *
     * @param cacheClass 缓存实现类，必须是Cache的子类
     * @param id 缓存实例的唯一标识
     * @return 根据指定类和标识创建的缓存实例
     *
     * 该方法通过反射机制调用缓存类的构造函数，创建缓存实例
     * 如果缓存实例创建失败，将抛出CacheException，包含失败原因
     */
    private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
        Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
        try {
            return cacheConstructor.newInstance(id);
        } catch (Exception e) {
            throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
        }
    }


    /**
     * 获取基础缓存类的构造函数
     *
     * @param cacheClass 缓存类的类型，该类应该继承自Cache
     * @return 返回一个构造函数，该构造函数可以创建一个接受String类型id的缓存类实例
     * @throws CacheException 如果缓存类没有实现一个接受String类型id的构造函数，则抛出此异常
     */
    private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
        try {
            // 尝试获取接受String类型id的构造函数
            return cacheClass.getConstructor(String.class);
        } catch (Exception e) {
            // 如果缓存类没有实现预期的构造函数，抛出自定义缓存异常
            throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  "
                + "Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e,
                e);
        }
    }


    private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
        Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
        try {
            return cacheConstructor.newInstance(base);
        } catch (Exception e) {
            throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
        }
    }

    private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
        try {
            return cacheClass.getConstructor(Cache.class);
        } catch (Exception e) {
            throw new CacheException("Invalid cache decorator (" + cacheClass + ").  "
                + "Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
        }
    }
}
