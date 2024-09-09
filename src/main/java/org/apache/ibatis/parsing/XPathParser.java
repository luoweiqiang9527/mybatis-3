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

import org.apache.ibatis.builder.BuilderException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XPathParser {

    private final Document document;
    private boolean validation;
    private EntityResolver entityResolver;
    // 路径解析需要用到设置好的属性
    private Properties variables;
    private XPath xpath;

    public XPathParser(String xml) {
        commonConstructor(false, null, null);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader) {
        commonConstructor(false, null, null);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream) {
        commonConstructor(false, null, null);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document) {
        commonConstructor(false, null, null);
        this.document = document;
    }

    public XPathParser(String xml, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = document;
    }

    public XPathParser(String xml, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = document;
    }

    public XPathParser(String xml, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = document;
    }

    public void setVariables(Properties variables) {
        this.variables = variables;
    }

    public String evalString(String expression) {
        return evalString(document, expression);
    }

    /**
     * 根据给定的XPath表达式和根对象评估字符串值
     * 此方法使用XPath评估表达式，然后解析结果字符串
     * 使用提供的变量替换结果中的占位符
     *
     * @param root       根对象，用于XPath评估的起点
     * @param expression XPath表达式，用于评估字符串值
     * @return 解析后的字符串，其中的占位符已被提供的变量替换
     */
    public String evalString(Object root, String expression) {
        // 使用XPath评估表达式，获得字符串类型的结果
        String result = (String) evaluate(expression, root, XPathConstants.STRING);
        // 解析评估结果中的占位符，返回解析后的字符串
        return PropertyParser.parse(result, variables);
    }


    public Boolean evalBoolean(String expression) {
        return evalBoolean(document, expression);
    }

    public Boolean evalBoolean(Object root, String expression) {
        return (Boolean) evaluate(expression, root, XPathConstants.BOOLEAN);
    }

    public Short evalShort(String expression) {
        return evalShort(document, expression);
    }

    public Short evalShort(Object root, String expression) {
        return Short.valueOf(evalString(root, expression));
    }

    public Integer evalInteger(String expression) {
        return evalInteger(document, expression);
    }

    public Integer evalInteger(Object root, String expression) {
        return Integer.valueOf(evalString(root, expression));
    }

    public Long evalLong(String expression) {
        return evalLong(document, expression);
    }

    public Long evalLong(Object root, String expression) {
        return Long.valueOf(evalString(root, expression));
    }

    public Float evalFloat(String expression) {
        return evalFloat(document, expression);
    }

    public Float evalFloat(Object root, String expression) {
        return Float.valueOf(evalString(root, expression));
    }

    public Double evalDouble(String expression) {
        return evalDouble(document, expression);
    }

    public Double evalDouble(Object root, String expression) {
        return (Double) evaluate(expression, root, XPathConstants.NUMBER);
    }

    public List<XNode> evalNodes(String expression) {
        return evalNodes(document, expression);
    }

    public List<XNode> evalNodes(Object root, String expression) {
        List<XNode> xnodes = new ArrayList<>();
        NodeList nodes = (NodeList) evaluate(expression, root, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            xnodes.add(new XNode(this, nodes.item(i), variables));
        }
        return xnodes;
    }

    /**
     * 根据给定的XPath表达式评估并返回对应的XNode节点.
     * 此方法提供了对文档的XPath表达式评估的简便封装.
     *
     * @param expression XPath表达式，用于定位文档中的节点.
     * @return 返回与XPath表达式匹配的XNode节点.
     * 如果没有找到匹配的节点，则返回null.
     */
    public XNode evalNode(String expression) {
        return evalNode(document, expression);
    }

    /**
     * 根据给定的XPath表达式，在指定的根节点下评估并返回一个XNode对象。
     * 这个方法主要用于解析XPath表达式，找到对应的节点，并封装为XNode对象返回。
     *
     * @param root       根节点，用于XPath表达式的解析起点。
     * @param expression XPath表达式，用于定位需要的节点。
     * @return 如果找到对应的节点，则返回一个封装了该节点的XNode对象；如果未找到，则返回null。
     */
    public XNode evalNode(Object root, String expression) {
        // 使用XPath表达式评估方法，尝试找到对应的节点
        Node node = (Node) evaluate(expression, root, XPathConstants.NODE);
        // 如果评估结果为null，则说明未找到对应的节点，直接返回null
        if (node == null) {
            return null;
        }
        // 如果找到了对应的节点，创建并返回一个新的XNode对象，其中包含评估得到的节点和变量信息
        return new XNode(this, node, variables);
    }


    /**
     * 评估指定根对象上的XPath表达式并返回结果。
     *
     * @param expression 要评估的XPath表达式。
     * @param root       用于评估XPath表达式的根对象。
     * @param returnType XPath表达式的预期返回类型。
     * @return 评估XPath表达式的结果。
     * @throws BuilderException 如果在评估XPath表达式时发生错误，将抛出BuilderException，并将原始异常作为其原因。
     */
    private Object evaluate(String expression, Object root, QName returnType) {
        try {
            // 尝试评估XPath表达式
            return xpath.evaluate(expression, root, returnType);
        } catch (Exception e) {
            // 如果在评估XPath表达式时发生错误，抛出BuilderException
            throw new BuilderException("评估XPath时发生错误。 原因: " + e, e);
        }
    }


    private Document createDocument(InputSource inputSource) {
        // important: this must only be called AFTER common constructor
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setValidating(validation);

            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(false);
            factory.setCoalescing(false);
            factory.setExpandEntityReferences(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(entityResolver);
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    // NOP
                }
            });
            return builder.parse(inputSource);
        } catch (Exception e) {
            throw new BuilderException("Error creating document instance.  Cause: " + e, e);
        }
    }

    /**
     * 构造函数，用于初始化包含验证标志、变量和实体解析器的公共属性
     * 此构造器主要用于配置XML解析器的状态，使其能够根据传入的参数进行XML文档的解析
     *
     * @param validation     表示是否启用验证的布尔标志，true为启用验证，false为不启用
     * @param variables      一组变量，用于在解析过程中提供动态值替换
     * @param entityResolver 实体解析器对象，用于解析XML文档中的外部实体
     */
    private void commonConstructor(boolean validation, Properties variables, EntityResolver entityResolver) {
        // 将传入的验证标志赋值给类属性validation
        this.validation = validation;
        // 将传入的实体解析器赋值给类属性entityResolver
        this.entityResolver = entityResolver;
        // 将传入的变量赋值给类属性variables
        this.variables = variables;

        // 创建一个新的XPathFactory实例，用于后续的XPath查询
        XPathFactory factory = XPathFactory.newInstance();
        // 使用XPathFactory实例创建一个新的XPath对象，用于执行XPath表达式
        this.xpath = factory.newXPath();
    }


}
