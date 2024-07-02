/*
 *    Copyright 2009-2022 the original author or authors.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.Reader;
import java.util.List;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.domain.blog.Author;
import org.apache.ibatis.domain.blog.PostLite;
import org.apache.ibatis.domain.blog.mappers.AuthorMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SqlSessionManagerTest extends BaseDataTest {

    private static SqlSessionManager manager;

    @BeforeAll
    static void setup() throws Exception {
        createBlogDataSource();
        final String resource = "org/apache/ibatis/builder/MapperConfig.xml";
        final Reader reader = Resources.getResourceAsReader(resource);
        manager = SqlSessionManager.newInstance(reader);
    }

    @Test
    void shouldThrowExceptionIfMappedStatementDoesNotExistAndSqlSessionIsOpen() {
        try {
            manager.startManagedSession();
            manager.selectList("ThisStatementDoesNotExist");
            fail("Expected exception to be thrown due to statement that does not exist.");
        } catch (PersistenceException e) {
            assertTrue(e.getMessage().contains("does not contain value for ThisStatementDoesNotExist"));
        } finally {
            manager.close();
        }
    }

    /**
     * 测试插入作者并提交事务的功能。
     * 该测试方法验证了通过AuthorMapper插入一个作者对象后，是否能成功提交事务，
     * 并且能从数据库中查询到刚刚插入的作者信息。
     */
    @Test
    void shouldCommitInsertedAuthor() {
        try {
            // 开始一个管理的会话，这个会话将在方法结束时自动提交或回滚
            manager.startManagedSession();
            // 获取AuthorMapper实例，用于数据库操作
            AuthorMapper mapper = manager.getMapper(AuthorMapper.class);
            // 创建一个预期要插入的作者对象
            Author expected = new Author(500, "cbegin", "******", "cbegin@somewhere.com", "Something...", null);
            // 插入作者对象到数据库
            mapper.insertAuthor(expected);
            // 提交事务，确保插入操作被持久化
            manager.commit();
            // 从数据库中根据ID查询刚刚插入的作者对象
            Author actual = mapper.selectAuthor(500);
            // 断言查询结果不为空，确认插入操作成功
            assertNotNull(actual);
        } finally {
            // 无论测试成功还是失败，都关闭会话管理器
            manager.close();
        }
    }


    @Test
    void shouldRollbackInsertedAuthor() {
        try {
            manager.startManagedSession();
            AuthorMapper mapper = manager.getMapper(AuthorMapper.class);
            Author expected = new Author(501, "lmeadors", "******", "lmeadors@somewhere.com", "Something...", null);
            mapper.insertAuthor(expected);
            manager.rollback();
            Author actual = mapper.selectAuthor(501);
            assertNull(actual);
        } finally {
            manager.close();
        }
    }

    @Test
    void shouldImplicitlyRollbackInsertedAuthor() {
        manager.startManagedSession();
        AuthorMapper mapper = manager.getMapper(AuthorMapper.class);
        Author expected = new Author(502, "emacarron", "******", "emacarron@somewhere.com", "Something...", null);
        mapper.insertAuthor(expected);
        manager.close();
        Author actual = mapper.selectAuthor(502);
        assertNull(actual);
    }

    @Test
    void shouldFindAllPostLites() throws Exception {
        List<PostLite> posts = manager.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.selectPostLite");
        assertEquals(2, posts.size()); // old gcode issue #392, new #1848
    }

    @Test
    void shouldFindAllMutablePostLites() throws Exception {
        List<PostLite> posts = manager.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.selectMutablePostLite");
        assertEquals(2, posts.size()); // old gcode issue #392, new #1848
    }

}
