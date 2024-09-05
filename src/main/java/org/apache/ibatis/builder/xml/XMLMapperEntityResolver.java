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
package org.apache.ibatis.builder.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.ibatis.io.Resources;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * MyBatis DTD的离线实体解析器。
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class XMLMapperEntityResolver implements EntityResolver {

    private static final String IBATIS_CONFIG_SYSTEM = "ibatis-3-config.dtd";
    private static final String IBATIS_MAPPER_SYSTEM = "ibatis-3-mapper.dtd";
    private static final String MYBATIS_CONFIG_SYSTEM = "mybatis-3-config.dtd";
    private static final String MYBATIS_MAPPER_SYSTEM = "mybatis-3-mapper.dtd";

    private static final String MYBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/mybatis-3-config.dtd";
    private static final String MYBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd";

    /**
     * 将公共DTD转换为本地DTD。
     *
     * @param publicId The public id that is what comes after "PUBLIC"
     * @param systemId The system id that is what comes after the public id.
     * @return The InputSource for the DTD
     * @throws org.xml.sax.SAXException If anything goes wrong
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
        try {
            if (systemId != null) {
                String lowerCaseSystemId = systemId.toLowerCase(Locale.ENGLISH);
                if (lowerCaseSystemId.contains(MYBATIS_CONFIG_SYSTEM) || lowerCaseSystemId.contains(IBATIS_CONFIG_SYSTEM)) {
                    return getInputSource(MYBATIS_CONFIG_DTD, publicId, systemId);
                }
                if (lowerCaseSystemId.contains(MYBATIS_MAPPER_SYSTEM) || lowerCaseSystemId.contains(IBATIS_MAPPER_SYSTEM)) {
                    return getInputSource(MYBATIS_MAPPER_DTD, publicId, systemId);
                }
            }
            return null;
        } catch (Exception e) {
            throw new SAXException(e.toString());
        }
    }

    /**
     * 根据给定的路径和ID获取InputSource对象
     * 该方法旨在为XML解析器提供输入源，以便从指定位置加载XML文档
     * 如果路径有效，则尝试打开资源并创建InputSource对象，同时设置公共ID和系统ID
     *
     * @param path 资源路径，用于定位XML文件如果路径为null，将返回null
     * @param publicId XML文档的公共标识符，用于解析过程可以在DTD中找到对应的公共ID
     * @param systemId XML文档的系统标识符，通常是指向XML文件的实际位置可以在DTD中找到对应的系统ID
     * @return 返回一个包含资源输入流、公共ID和系统ID的InputSource对象如果无法访问资源，则返回null
     */
    private InputSource getInputSource(String path, String publicId, String systemId) {
        InputSource source = null;
        // 检查路径是否已经提供
        if (path != null) {
            try {
                // 尝试根据路径打开资源的输入流
                InputStream in = Resources.getResourceAsStream(path);
                // 使用输入流创建InputSource对象
                source = new InputSource(in);
                // 设置InputSource的公共ID和系统ID，以便在解析过程中使用
                source.setPublicId(publicId);
                source.setSystemId(systemId);
            } catch (IOException e) {
                // 如果发生IO异常，忽略异常，认为是无法访问资源，返回null
                // 这里选择不记录异常，因为函数的设计允许返回null作为有效结果
            }
        }
        return source;
    }

}
