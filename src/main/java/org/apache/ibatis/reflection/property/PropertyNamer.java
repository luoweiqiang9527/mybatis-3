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
package org.apache.ibatis.reflection.property;

import java.util.Locale;

import org.apache.ibatis.reflection.ReflectionException;

/**
 * @author Clinton Begin
 */
public final class PropertyNamer {

    private PropertyNamer() {
        // Prevent Instantiation of Static Class
    }

    /**
     * 将方法名转换为对应的属性名。
     * 该转换适用于从"get", "set", 或 "is"开头的方法名转换为对应的属性名。
     * 如果方法名以"is"开头，那么移除前两个字符；
     * 如果方法名以"get"或"set"开头，那么移除前三个字符；
     * 如果方法名不以这些前缀开头，则抛出ReflectionException异常。
     * 如果移除前缀后的属性名只有一个字符，或者除了第一个字符外，后续字符不是大写，则将第一个字符转换为小写，并返回转换后的属性名。
     *
     * @param name 待转换的方法名，应以"get", "set", 或 "is"开头。
     * @return 转换后的属性名。
     * @throws ReflectionException 如果方法名不以"get", "set", 或 "is"开头，则抛出此异常。
     */
    public static String methodToProperty(String name) {
        // 移除方法名前的"get", "set", 或 "is"前缀
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get") || name.startsWith("set")) {
            name = name.substring(3);
        } else {
            // 如果方法名没有以预期的前缀开头，则抛出异常
            throw new ReflectionException(
                "Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
        }

        // 将移除前缀后的属性名首字母转换为小写（如果只有一个字符则不变）
        if (name.length() == 1 || name.length() > 1 && !Character.isUpperCase(name.charAt(1))) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }

        return name;
    }

    public static boolean isProperty(String name) {
        return isGetter(name) || isSetter(name);
    }

    /**
     * 判断传入的方法名是否为标准的Java Getter方法。
     * Getter方法通常以get或is开头，且后续字符数满足特定要求。
     *
     * @param name 待判断的方法名
     * @return 如果方法名是以get开头且长度大于3，或者是以is开头且长度大于2，则返回true；否则返回false。
     */
    public static boolean isGetter(String name) {
        // 判断方法名是否以get开头且长度大于3，或以is开头且长度大于2
        return name.startsWith("get") && name.length() > 3 || name.startsWith("is") && name.length() > 2;
    }

    /**
     * 判断给定的方法名是否为标准的setter方法名。
     *
     * @param name 待判断的方法名。
     * @return 如果方法名以"set"开头，并且长度大于3个字符，则认为是setter方法，返回true；否则返回false。
     */
    public static boolean isSetter(String name) {
        // 检查方法名是否以"set"开头且长度大于3，这是setter方法的一般定义。
        return name.startsWith("set") && name.length() > 3;
    }

}
