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
/**
 * 属性分词器类，用于解析属性的全名（包括索引）到其各个组成部分。
 * 它实现了Iterator接口，允许迭代访问属性的子属性。
 */
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性分词器
 *
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
    // 当前属性的名称
    private String name;
    // 当前属性的全名，包括索引
    private final String indexedName;
    // 当前属性的索引值，如果属性没有索引，则为null
    private String index;
    // 当前属性的子属性名称，如果当前属性没有子属性，则为null
    private final String children;

    /**
     * 构造函数，初始化PropertyTokenizer实例。
     *
     * @param fullname 属性的全名，可以包括索引和子属性。
     */
    public PropertyTokenizer(String fullname) {
        // 寻找第一个.字符，用于分割属性名称和子属性名称
        int delim = fullname.indexOf('.');
        if (delim > -1) {
            // 如果存在.，则说明有子属性
            name = fullname.substring(0, delim);
            children = fullname.substring(delim + 1);
        } else {
            // 如果不存在.，则说明没有子属性
            name = fullname;
            children = null;
        }
        indexedName = name;
        // 寻找第一个[字符，用于分割属性名称和索引
        delim = name.indexOf('[');
        if (delim > -1) {
            // 如果存在[，则说明有索引
            index = name.substring(delim + 1, name.length() - 1);
            name = name.substring(0, delim);
        }
    }

    /**
     * 获取当前属性的名称。
     *
     * @return 当前属性的名称。
     */
    public String getName() {
        return name;
    }

    /**
     * 获取当前属性的索引值。
     *
     * @return 当前属性的索引值，如果属性没有索引，则返回null。
     */
    public String getIndex() {
        return index;
    }

    /**
     * 获取当前属性的全名，包括索引。
     *
     * @return 当前属性的全名，包括索引。
     */
    public String getIndexedName() {
        return indexedName;
    }

    /**
     * 获取当前属性的子属性名称。
     *
     * @return 当前属性的子属性名称，如果当前属性没有子属性，则返回null。
     */
    public String getChildren() {
        return children;
    }

    /**
     * 检查是否还有子属性可以迭代。
     *
     * @return 如果有子属性，则返回true；否则返回false。
     */
    @Override
    public boolean hasNext() {
        return children != null;
    }

    /**
     * 获取下一个子属性的分词器。
     *
     * @return 下一个子属性的分词器。
     */
    @Override
    public PropertyTokenizer next() {
        return new PropertyTokenizer(children);
    }

    /**
     * 由于PropertyTokenizer不支持删除操作，此方法抛出UnsupportedOperationException。
     *
     * @throws UnsupportedOperationException 因为删除操作不被支持。
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException(
            "Remove is not supported, as it has no meaning in the context of properties.");
    }
}


