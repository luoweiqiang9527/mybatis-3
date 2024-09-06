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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

class XPathParserTest {
    private final String resource = "nodelet_test.xml";

    /**
     * 测试构造函数：验证带有输入流和解析器的构造创建过程
     * 该测试方法专注于验证是否可以正确地使用 InputStream 和 EntityResolver
     * 创建 XPathParser 对象，并执行测试评估方法
     *
     * @throws Exception 如果资源文件无法打开或解析时抛出异常
     */
    @Test
    void constructorWithInputStreamValidationVariablesEntityResolver() throws Exception {

        // 从资源文件创建输入流
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            // 使用输入流初始化 XPathParser，不启用验证，不使用命名空间上下文
            XPathParser parser = new XPathParser(inputStream, false, null, null);
            // 执行 XPathParser 的测试评估方法
            testEvalMethod(parser);
        }
    }


    @Test
    void constructorWithInputStreamValidationVariables() throws IOException {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XPathParser parser = new XPathParser(inputStream, false, null);
            testEvalMethod(parser);
        }
    }

    @Test
    void constructorWithInputStreamValidation() throws IOException {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XPathParser parser = new XPathParser(inputStream, false);
            testEvalMethod(parser);
        }
    }

    @Test
    void constructorWithInputStream() throws IOException {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XPathParser parser = new XPathParser(inputStream);
            testEvalMethod(parser);
        }
    }

    // Reader Source
    @Test
    void constructorWithReaderValidationVariablesEntityResolver() throws Exception {

        try (Reader reader = Resources.getResourceAsReader(resource)) {
            XPathParser parser = new XPathParser(reader, false, null, null);
            testEvalMethod(parser);
        }
    }

    @Test
    void constructorWithReaderValidationVariables() throws IOException {
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            XPathParser parser = new XPathParser(reader, false, null);
            testEvalMethod(parser);
        }
    }

    @Test
    void constructorWithReaderValidation() throws IOException {
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            XPathParser parser = new XPathParser(reader, false);
            testEvalMethod(parser);
        }
    }

    @Test
    void constructorWithReader() throws IOException {
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            XPathParser parser = new XPathParser(reader);
            testEvalMethod(parser);
        }
    }

    // Xml String Source
    @Test
    void constructorWithStringValidationVariablesEntityResolver() throws Exception {
        XPathParser parser = new XPathParser(getXmlString(resource), false, null, null);
        testEvalMethod(parser);
    }

    @Test
    void constructorWithStringValidationVariables() throws IOException {
        XPathParser parser = new XPathParser(getXmlString(resource), false, null);
        testEvalMethod(parser);
    }

    @Test
    void constructorWithStringValidation() throws IOException {
        XPathParser parser = new XPathParser(getXmlString(resource), false);
        testEvalMethod(parser);
    }

    @Test
    void constructorWithString() throws IOException {
        XPathParser parser = new XPathParser(getXmlString(resource));
        testEvalMethod(parser);
    }

    // Document Source
    @Test
    void constructorWithDocumentValidationVariablesEntityResolver() {
        XPathParser parser = new XPathParser(getDocument(resource), false, null, null);
        testEvalMethod(parser);
    }

    @Test
    void constructorWithDocumentValidationVariables() {
        XPathParser parser = new XPathParser(getDocument(resource), false, null);
        testEvalMethod(parser);
    }

    @Test
    void constructorWithDocumentValidation() {
        XPathParser parser = new XPathParser(getDocument(resource), false);
        testEvalMethod(parser);
    }

    @Test
    void constructorWithDocument() {
        XPathParser parser = new XPathParser(getDocument(resource));
        testEvalMethod(parser);
    }

    private Document getDocument(String resource) {
        try {
            InputSource inputSource = new InputSource(Resources.getResourceAsReader(resource));
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(false);
            factory.setCoalescing(false);
            factory.setExpandEntityReferences(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(inputSource);// already closed resource in builder.parse method
        } catch (Exception e) {
            throw new BuilderException("Error creating document instance.  Cause: " + e, e);
        }
    }

    private String getXmlString(String resource) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(Resources.getResourceAsReader(resource))) {
            StringBuilder sb = new StringBuilder();
            String temp;
            while ((temp = bufferedReader.readLine()) != null) {
                sb.append(temp);
            }
            return sb.toString();
        }
    }

    enum EnumTest {
        YES, NO
    }

    /**
     * 验证XPathParser的各种评估方法是否正常工作.
     * 这个方法通过一系列断言操作来验证XPathParser对不同数据类型节点的评估方法是否返回正确的值.
     * 它确保了XPath表达式能够正确解析并返回预期的数据类型和值.
     *
     * @param parser XPathParser的实例，用于评估XPath表达式.
     */
    private void testEvalMethod(XPathParser parser) {
        // 验证长整型节点的评估
        assertEquals((Long) 1970L, parser.evalLong("/employee/birth_date/year"));
        assertEquals((Long) 1970L, parser.evalNode("/employee/birth_date/year").getLongBody());

        // 验证短整型节点的评估
        assertEquals((short) 6, (short) parser.evalShort("/employee/birth_date/month"));

        // 验证整型节点的评估
        assertEquals((Integer) 15, parser.evalInteger("/employee/birth_date/day"));
        assertEquals((Integer) 15, parser.evalNode("/employee/birth_date/day").getIntBody());

        // 验证浮点型节点的评估
        assertEquals((Float) 5.8f, parser.evalFloat("/employee/height"));
        assertEquals((Float) 5.8f, parser.evalNode("/employee/height").getFloatBody());

        // 验证双精度浮点型节点的评估
        assertEquals((Double) 5.8d, parser.evalDouble("/employee/height"));
        assertEquals((Double) 5.8d, parser.evalNode("/employee/height").getDoubleBody());

        // 验证字符串节点的评估
        assertEquals("${id_var}", parser.evalString("/employee/@id"));
        assertEquals("${id_var}", parser.evalNode("/employee/@id").getStringBody());

        // 验证布尔节点的评估
        assertEquals(Boolean.TRUE, parser.evalBoolean("/employee/active"));
        assertEquals(Boolean.TRUE, parser.evalNode("/employee/active").getBooleanBody());

        // 验证枚举节点的评估
        assertEquals(EnumTest.YES, parser.evalNode("/employee/active").getEnumAttribute(EnumTest.class, "bot"));

        // 验证属性节点的评估
        assertEquals((Float) 3.2f, parser.evalNode("/employee/active").getFloatAttribute("score"));
        assertEquals((Double) 3.2d, parser.evalNode("/employee/active").getDoubleAttribute("score"));

        // 验证节点的路径和基于值的标识符
        XNode node = parser.evalNode("/employee/height");
        assertEquals("employee/height", node.getPath());
        assertEquals("employee[${id_var}]_height", node.getValueBasedIdentifier());

        // 验证节点列表的大小
        assertEquals(7, parser.evalNodes("/employee/*").size());

        // 验证节点转换为字符串的输出
        assertEquals("<id>\n  ${id_var}\n</id>", parser.evalNode("/employee/@id").toString().trim());
    }


}
