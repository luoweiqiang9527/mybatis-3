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
package org.apache.ibatis.submitted.criterion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CriterionTest {

    protected static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUp() throws Exception {
        try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/submitted/criterion/MapperConfig.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }

        BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
            "org/apache/ibatis/submitted/criterion/CreateDB.sql");
    }

    @Test
    void testSimpleSelect() {
        // 使用try-with-resources语句自动关闭SqlSession
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            // 创建Criterion实例，并设置查询条件
            Criterion criterion = new Criterion();
            criterion.setTest("firstName =");
            criterion.setValue("Fred");

            // 创建Parameter实例，并将上面的Criterion实例设置为其条件
            Parameter parameter = new Parameter();
            parameter.setCriterion(criterion);

            // 执行查询，传入SQL映射文件中定义的selectList语句和参数
            List<Map<String, Object>> answer = sqlSession.selectList("org.apache.ibatis.submitted.criterion.simpleSelect",
                parameter);

            // 验证查询结果是否符合预期，即只返回一个结果
            assertEquals(1, answer.size());
        }

    }
}
