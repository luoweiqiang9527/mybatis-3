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

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

    private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
    /**
     * The special property key that indicate whether enable a default value on placeholder.
     * <p>
     * The default value is {@code false} (indicate disable a default value on placeholder) If you specify the
     * {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
     * </p>
     *
     * @since 3.4.2
     */
    public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

    /**
     * The special property key that specify a separator for key and default value on placeholder.
     * <p>
     * The default separator is {@code ":"}.
     * </p>
     *
     * @since 3.4.2
     */
    public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

    private static final String ENABLE_DEFAULT_VALUE = "false";
    private static final String DEFAULT_VALUE_SEPARATOR = ":";

    private PropertyParser() {
        // Prevent Instantiation
    }

    /**
     * 解析字符串中的变量并替换为实际值
     * <p>
     * 该方法主要用于解析传入的字符串 string，寻找以 "${" 开始、"}" 结束的变量标识符，
     * 并使用提供的变量集合 variables 来替换这些变量标识符为实际的值
     *
     * @param string    需要解析的字符串，可能包含需要替换的变量标识符
     * @param variables 包含变量名与实际值的映射关系的集合
     * @return 替换变量后的字符串
     */
    public static String parse(String string, Properties variables) {
        // 创建一个 VariableTokenHandler 实例，用于处理变量替换，传入变量集合以便进行变量替换
        VariableTokenHandler handler = new VariableTokenHandler(variables);
        // 创建一个 GenericTokenParser 实例，指定变量开始和结束的标识符，以及处理变量的 TokenHandler
        GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
        // 使用解析器处理输入的字符串，完成变量的替换并返回结果
        return parser.parse(string);
    }


    private static class VariableTokenHandler implements TokenHandler {
        private final Properties variables;
        private final boolean enableDefaultValue;
        private final String defaultValueSeparator;

        private VariableTokenHandler(Properties variables) {
            this.variables = variables;
            this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
            this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
        }

        private String getPropertyValue(String key, String defaultValue) {
            return variables == null ? defaultValue : variables.getProperty(key, defaultValue);
        }

        /**
         * 处理占位符令牌
         * 如果变量集非空，尝试用变量值替换占位符
         * 当允许默认值且占位符包含默认值分隔符时，使用默认值作为回退
         *
         * @param content 占位符的内容，可能包含默认值
         * @return 替换后的字符串，如果变量集不包含该占位符，则返回原始占位符
         */
        @Override
        public String handleToken(String content) {
            // 检查变量集是否非空
            if (variables != null) {
                String key = content;
                // 如果允许使用默认值
                if (enableDefaultValue) {
                    // 查找默认值分隔符的位置
                    final int separatorIndex = content.indexOf(defaultValueSeparator);
                    String defaultValue = null;
                    // 如果找到了分隔符
                    if (separatorIndex >= 0) {
                        // 重新定义键，不包含默认值部分
                        key = content.substring(0, separatorIndex);
                        // 获取默认值
                        defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
                    }
                    // 如果有默认值，尝试从变量集中获取值，否则使用默认值
                    if (defaultValue != null) {
                        return variables.getProperty(key, defaultValue);
                    }
                }
                // 如果变量集中包含该键，直接返回其值
                if (variables.containsKey(key)) {
                    return variables.getProperty(key);
                }
            }
            // 如果变量集不包含该键，返回原始占位符
            return "${" + content + "}";
        }

    }

}
