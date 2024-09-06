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

/**
 * 通用令牌解析器
 *
 * @author Clinton Begin
 */
public class GenericTokenParser {
    // 开始标记
    private final String openToken;
    // 结束标记
    private final String closeToken;
    // 标记处理器
    private final TokenHandler handler;

    public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
        this.openToken = openToken;
        this.closeToken = closeToken;
        this.handler = handler;
    }

    /**
     * 解析文本中的特殊令牌，并将其替换为处理结果
     *
     * @param text 要解析的文本
     * @return 解析后的文本
     */
    public String parse(String text) {
        // 检查输入文本是否为空
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 搜索开启令牌
        int start = text.indexOf(openToken);
        // 如果没有找到开启令牌，直接返回原文本
        if (start == -1) {
            return text;
        }
        char[] src = text.toCharArray();
        int offset = 0;
        final StringBuilder builder = new StringBuilder();
        StringBuilder expression = null;
        // 循环处理文本中的每个开启令牌
        do {
            // 判断开启令牌前是否有转义字符
            if (start > 0 && src[start - 1] == '\\') {
                // 这个开启令牌是被转义的，移除转义字符并继续
                builder.append(src, offset, start - offset - 1).append(openToken);
                offset = start + openToken.length();
            } else {
                // 找到开启令牌，开始搜索关闭令牌
                if (expression == null) {
                    expression = new StringBuilder();
                } else {
                    expression.setLength(0);
                }
                builder.append(src, offset, start - offset);
                offset = start + openToken.length();
                int end = text.indexOf(closeToken, offset);
                // 搜索未转义的关闭令牌
                while (end > -1) {
                    // 判断关闭令牌前是否有转义字符
                    if ((end <= offset) || (src[end - 1] != '\\')) {
                        // 没有被转义，添加到表达式中并跳出循环
                        expression.append(src, offset, end - offset);
                        break;
                    }
                    // 这个关闭令牌是被转义的，移除转义字符并继续
                    expression.append(src, offset, end - offset - 1).append(closeToken);
                    offset = end + closeToken.length();
                    end = text.indexOf(closeToken, offset);
                }
                // 如果没有找到关闭令牌，将剩余文本添加到结果中
                if (end == -1) {
                    builder.append(src, start, src.length - start);
                    offset = src.length;
                } else {
                    // 找到关闭令牌，处理表达式并添加到结果中
                    builder.append(handler.handleToken(expression.toString()));
                    offset = end + closeToken.length();
                }
            }
            // 继续搜索下一个开启令牌
            start = text.indexOf(openToken, offset);
        } while (start > -1);
        // 将剩余的文本添加到结果中
        if (offset < src.length) {
            builder.append(src, offset, src.length - offset);
        }
        return builder.toString();
    }
}
