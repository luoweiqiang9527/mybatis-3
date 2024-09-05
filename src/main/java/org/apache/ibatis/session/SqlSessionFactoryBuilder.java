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
package org.apache.ibatis.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * Builds {@link SqlSession} instances.
 *
 * @author Clinton Begin
 */
public class SqlSessionFactoryBuilder {

    public SqlSessionFactory build(Reader reader) {
        return build(reader, null, null);
    }

    public SqlSessionFactory build(Reader reader, String environment) {
        return build(reader, environment, null);
    }

    public SqlSessionFactory build(Reader reader, Properties properties) {
        return build(reader, null, properties);
    }

    /**
     * 构建SqlSessionFactory对象
     *
     * @param reader 提供配置信息的Reader对象
     * @param environment 配置所使用的环境ID，允许覆盖在mybatis配置文件中默认指定的环境
     * @param properties 传递给配置文件中使用占位符的属性值，允许动态替换配置文件中的属性
     * @return 构建好的SqlSessionFactory对象
     */
    public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
        try {
            // 使用XMLConfigBuilder解析配置信息
            XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
            // 调用内部方法build，根据解析后的配置构建SqlSessionFactory
            return build(parser.parse());
        } catch (Exception e) {
            // 捕获异常并包装，提供更明确的错误信息
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            // 重置ErrorContext，以避免潜在的状态污染
            ErrorContext.instance().reset();
            try {
                // 尝试关闭Reader资源，避免资源泄露
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // 忽略IO异常，优先处理之前的错误
            }
        }
    }

    public SqlSessionFactory build(InputStream inputStream) {
        return build(inputStream, null, null);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment) {
        return build(inputStream, environment, null);
    }

    public SqlSessionFactory build(InputStream inputStream, Properties properties) {
        return build(inputStream, null, properties);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
        try {
            XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

    public SqlSessionFactory build(Configuration config) {
        return new DefaultSqlSessionFactory(config);
    }

}
