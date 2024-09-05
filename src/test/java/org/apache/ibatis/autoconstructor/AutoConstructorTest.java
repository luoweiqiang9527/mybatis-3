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
package org.apache.ibatis.autoconstructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Reader;
import java.util.List;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AutoConstructorTest {
    private static SqlSessionFactory sqlSessionFactory;

    /**
     * 使用@BeforeAll注解的静态方法，用于在所有测试方法执行之前进行一次性的测试环境设置
     * 包括创建SqlSessionFactory实例和填充内存数据库
     * 该方法可能抛出Exception异常，因此调用方需要处理或声明抛出该异常
     */
    @BeforeAll
    static void setUp() throws Exception {
        // 从配置资源中创建SqlSessionFactory实例
        try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/autoconstructor/mybatis-config.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }

        // 使用SqlSessionFactory的配置信息来获取DataSource，并执行SQL脚本以创建数据库结构
        BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
            "org/apache/ibatis/autoconstructor/CreateDB.sql");
    }


    /**
     * 此方法用于测试从数据库中完全填充一个主题对象。
     * 方法不接受参数，也不返回任何值。
     * 它会尝试打开一个SQL会话，使用AutoConstructorMapper来获取主题对象，并确保该对象不为空。
     */
    @Test
    void fullyPopulatedSubject() {
        // 使用try-with-resources语句自动关闭SqlSession
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            // 获取AutoConstructorMapper的实例
            final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
            // 通过mapper实例获取id为1的主题对象
            final Object subject = mapper.getSubject(1);
            // 断言确保subject对象不为空
            assertNotNull(subject);
        }
    }


    /**
     * 尝试从数据库中获取主题信息的示例方法。
     * 这个方法尝试打开一个SQL会话，映射到AutoConstructorMapper接口，然后使用这个映射器来获取主题信息。
     * 如果获取过程中发生持久化异常（PersistenceException），则断言会抛出该异常。
     *
     * 注意：此方法不接受参数，也不返回任何值。
     */
    @Test
    void primitiveSubjects() {
        // 尝试使用SqlSessionFactory打开一个SqlSession，并在使用后自动关闭
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            // 获取AutoConstructorMapper接口的实例，用于执行数据库操作
            final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
            // 断言执行mapper的getSubjects方法时，会抛出PersistenceException异常
            assertThrows(PersistenceException.class, mapper::getSubjects);
        }
    }


    @Test
    void annotatedSubject() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
            verifySubjects(mapper.getAnnotatedSubjects());
        }
    }

    @Test
    void badMultipleAnnotatedSubject() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
            final PersistenceException ex = assertThrows(PersistenceException.class, mapper::getBadAnnotatedSubjects);
            final ExecutorException cause = (ExecutorException) ex.getCause();
            assertEquals("@AutomapConstructor should be used in only one constructor.", cause.getMessage());
        }
    }

    @Test
    void badSubject() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
            assertThrows(PersistenceException.class, mapper::getBadSubjects);
        }
    }

    @Test
    void extensiveSubject() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
            verifySubjects(mapper.getExtensiveSubjects());
        }
    }

    private void verifySubjects(final List<?> subjects) {
        assertNotNull(subjects);
        Assertions.assertThat(subjects.size()).isEqualTo(3);
    }
}
