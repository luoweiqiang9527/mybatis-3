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
package org.apache.ibatis.parsing;

import java.util.Properties;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PropertyParserTest {

    /**
     * 测试属性解析功能，包括默认值和实际值的解析
     * 该测试验证了在不同配置下，属性解析器对属性的解析是否符合预期
     */
    @Test
    void replaceToVariableValue() {
        // 创建新的属性集合
        Properties props = new Properties();

        // 设置是否启用默认值为true
        props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");

        // 添加键值对属性
        props.setProperty("key", "value");
        props.setProperty("tableName", "members");
        props.setProperty("orderColumn", "member_id");
        props.setProperty("a:b", "c");

        // 验证当启用默认值时，解析器能正确解析到实际值
        Assertions.assertThat(PropertyParser.parse("${key}", props)).isEqualTo("value");
        Assertions.assertThat(PropertyParser.parse("${key:aaaa}", props)).isEqualTo("value");
        Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${tableName:users} ORDER BY ${orderColumn:id}", props))
            .isEqualTo("SELECT * FROM members ORDER BY member_id");

        // 设置是否启用默认值为false
        props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "false");

        // 验证当禁用默认值时，解析器能正确解析到实际值
        Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("c");

        // 移除是否启用默认值的设置
        props.remove(PropertyParser.KEY_ENABLE_DEFAULT_VALUE);

        // 验证当没有设置是否启用默认值时，解析器仍然能正确解析到实际值
        Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("c");

    }


    /**
     * 测试在不同配置下，验证属性解析的行为.
     * 特别是当默认值替换被启用和禁用时，确保属性解析器正确处理未找到的键.
     */
    @Test
    void notReplace() {
        // 初始化属性集
        Properties props = new Properties();

        // 当启用默认值替换时，确保未解析的属性保持原样
        props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
        Assertions.assertThat(PropertyParser.parse("${key}", props)).isEqualTo("${key}");
        Assertions.assertThat(PropertyParser.parse("${key}", null)).isEqualTo("${key}");

        // 当禁用默认值替换时，确保复杂格式的属性也保持未解析状态
        props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "false");
        Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("${a:b}");

        // 移除默认值替换的配置，确保属性解析不受之前状态的影响
        props.remove(PropertyParser.KEY_ENABLE_DEFAULT_VALUE);
        Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("${a:b}");
    }


    /**
     * 测试属性解析器在启用了默认值功能后的表现
     * 此方法验证了在不同场景下，属性解析器如何处理带有默认值的属性
     */
    @Test
    void applyDefaultValue() {
        // 初始化属性集，并启用默认值支持
        Properties props = new Properties();
        props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");

        // 测试基本的默认值解析功能
        Assertions.assertThat(PropertyParser.parse("${key:default}", props)).isEqualTo("default");

        // 测试在SQL查询中使用默认值的情况
        Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${tableName:users} ORDER BY ${orderColumn:id}", props))
            .isEqualTo("SELECT * FROM users ORDER BY id");

        // 测试在未提供默认值时，解析器应返回空字符串的行为
        Assertions.assertThat(PropertyParser.parse("${key:}", props)).isEmpty();

        // 测试默认值为空格的解析情况
        Assertions.assertThat(PropertyParser.parse("${key: }", props)).isEqualTo(" ");

        // 测试当默认值标记被重复使用时的情况
        Assertions.assertThat(PropertyParser.parse("${key::}", props)).isEqualTo(":");
    }


    /**
     * 测试应用自定义分隔符的功能
     * 此测试验证了属性解析器在启用自定义分隔符时的正确行为
     */
    @Test
    void applyCustomSeparator() {
        // 初始化属性集
        Properties props = new Properties();
        // 设置默认值启用标志为true
        props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
        // 设置自定义的默认值分隔符
        props.setProperty(PropertyParser.KEY_DEFAULT_VALUE_SEPARATOR, "?:");

        // 测试基本默认值解析
        Assertions.assertThat(PropertyParser.parse("${key?:default}", props)).isEqualTo("default");

        // 测试复杂默认值表达式解析
        Assertions
            .assertThat(PropertyParser.parse(
                "SELECT * FROM ${schema?:prod}.${tableName == null ? 'users' : tableName} ORDER BY ${orderColumn}", props))
            .isEqualTo("SELECT * FROM prod.${tableName == null ? 'users' : tableName} ORDER BY ${orderColumn}");

        // 测试空默认值情况
        Assertions.assertThat(PropertyParser.parse("${key?:}", props)).isEmpty();

        // 测试默认值为空格的情况
        Assertions.assertThat(PropertyParser.parse("${key?: }", props)).isEqualTo(" ");

        // 测试默认值为自定义分隔符的情况
        Assertions.assertThat(PropertyParser.parse("${key?::}", props)).isEqualTo(":");
    }


}
