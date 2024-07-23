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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javassist.util.proxy.Proxy;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.domain.blog.Author;
import org.apache.ibatis.domain.blog.Blog;
import org.apache.ibatis.domain.blog.Comment;
import org.apache.ibatis.domain.blog.DraftPost;
import org.apache.ibatis.domain.blog.ImmutableAuthor;
import org.apache.ibatis.domain.blog.Post;
import org.apache.ibatis.domain.blog.Section;
import org.apache.ibatis.domain.blog.Tag;
import org.apache.ibatis.domain.blog.mappers.AuthorMapper;
import org.apache.ibatis.domain.blog.mappers.AuthorMapperWithMultipleHandlers;
import org.apache.ibatis.domain.blog.mappers.AuthorMapperWithRowBounds;
import org.apache.ibatis.domain.blog.mappers.BlogMapper;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SqlSessionTest extends BaseDataTest {
    private static SqlSessionFactory sqlMapper;

    /**
     * 集群初始化设置。
     * 本方法在所有测试方法执行前仅执行一次，负责配置和初始化与数据库相关的资源。
     * 使用了静态块来确保这些资源在测试类的生命周期内只被初始化一次。
     *
     * @throws Exception 如果资源配置或数据库连接失败，抛出异常。
     */
    @BeforeAll
    static void setup() throws Exception {
        // 创建博客数据源。这是为了在测试环境中模拟真实数据库的行为。
        createBlogDataSource();

        // 定义MyBatis配置文件的位置。
        final String resource = "org/apache/ibatis/builder/MapperConfig.xml";

        // 加载MyBatis配置文件。
        final Reader reader = Resources.getResourceAsReader(resource);

        // 根据配置文件构建SqlSessionFactory，它是MyBatis的核心对象，用于管理SQL会话。
        sqlMapper = new SqlSessionFactoryBuilder().build(reader);
    }


    /**
     * 测试Configuration类是否能够正确解析缓存的简短名称和完整限定名称。
     * 该测试通过创建一个配置对象，添加一个具有完整限定名称的缓存实例，然后分别使用完整限定名称和简短名称来检索缓存，
     * 以验证Configuration对象能否正确识别和返回相同的缓存实例。
     */
    @Test
    void shouldResolveBothSimpleNameAndFullyQualifiedName() {
        // 创建一个Configuration实例
        Configuration c = new Configuration();
        // 定义一个缓存的完整限定类名
        final String fullName = "com.mycache.MyCache";
        // 定义相同缓存的简短类名
        final String shortName = "MyCache";
        // 创建一个具有完整限定名称的缓存实例
        final PerpetualCache cache = new PerpetualCache(fullName);
        // 将缓存实例添加到配置中
        c.addCache(cache);
        // 验证通过完整限定名称获取的缓存是否与原始缓存实例相同
        assertEquals(cache, c.getCache(fullName));
        // 验证通过简短名称获取的缓存是否与原始缓存实例相同
        assertEquals(cache, c.getCache(shortName));
    }


    /**
     * 测试在无法找到确切缓存时是否能正确切换到最适用的简名。
     * 此测试验证了当请求的缓存名称完全限定名不正确时，配置是否能退回到使用最匹配的简单名称。
     * 它首先创建一个配置对象，并添加一个具有完全限定名的缓存实例。
     * 然后，测试通过使用正确的完全限定名来验证缓存的检索。
     * 最后，测试尝试使用一个不正确的命名空间的完全限定名来检索缓存，期望抛出IllegalArgumentException。
     */
    @Test
    void shouldFailOverToMostApplicableSimpleName() {
        // 创建一个配置实例
        Configuration c = new Configuration();
        // 定义一个正确的完全限定缓存类名
        final String fullName = "com.mycache.MyCache";
        // 定义一个错误的命名空间的完全限定缓存类名
        final String invalidName = "unknown.namespace.MyCache";
        // 创建一个缓存实例并使用正确的完全限定名初始化
        final PerpetualCache cache = new PerpetualCache(fullName);
        // 将缓存实例添加到配置中
        c.addCache(cache);
        // 验证能否使用完全限定名正确检索到缓存
        assertEquals(cache, c.getCache(fullName));
        // 验证使用错误的命名空间的完全限定名检索缓存时，是否抛出IllegalArgumentException
        Assertions.assertThrows(IllegalArgumentException.class, () -> c.getCache(invalidName));
    }


    /**
     * 测试在存在命名冲突的情况下，配置中获取缓存的ambiguity行为。
     * 该测试期望在尝试获取一个非唯一匹配的简短名称缓存时抛出异常。
     * <p>
     * 测试首先创建并配置了两个具有相同类名但不同包名的缓存实例。
     * 然后，通过它们的全限定名和一个共享简短名称来检索这些缓存。
     * 预期在尝试使用简短名称获取缓存时会失败，并抛出说明ambiguity的异常。
     */
    @Test
    void shouldSucceedWhenFullyQualifiedButFailDueToAmbiguity() {
        // 创建一个新的配置实例
        Configuration c = new Configuration();

        // 初始化并添加第一个缓存实例
        final String name1 = "com.mycache.MyCache";
        final PerpetualCache cache1 = new PerpetualCache(name1);
        c.addCache(cache1);

        // 初始化并添加第二个缓存实例，与第一个缓存只有包名不同
        final String name2 = "com.other.MyCache";
        final PerpetualCache cache2 = new PerpetualCache(name2);
        c.addCache(cache2);

        // 定义一个共享的简短名称，会导致获取缓存时的ambiguity
        final String shortName = "MyCache";

        // 验证能否通过全限定名正确获取到缓存实例
        assertEquals(cache1, c.getCache(name1));
        assertEquals(cache2, c.getCache(name2));

        // 尝试使用共享的简短名称获取缓存，并期望抛出异常
        try {
            c.getCache(shortName);
            fail("Exception expected due to ambiguity.");
        } catch (Exception e) {
            // 验证异常消息中是否包含"ambiguous（模糊）"关键词
            assertTrue(e.getMessage().contains("ambiguous"));
        }

    }


    /**
     * 测试添加缓存时是否能正确处理名称冲突。
     * 该测试期望在尝试添加一个已经存在的缓存时抛出异常。
     * <p>
     * 测试流程：
     * 1. 创建一个Configuration实例。
     * 2. 创建一个名为"com.mycache.MyCache"的PerpetualCache实例。
     * 3. 尝试将该缓存实例添加到Configuration中两次。
     * 4. 期望第二次添加时抛出异常，因为缓存名称已存在。
     * 5. 如果抛出异常，则测试通过；否则，测试失败。
     */
    @Test
    void shouldFailToAddDueToNameConflict() {
        // 初始化Configuration对象和一个PerpetualCache对象
        Configuration c = new Configuration();
        final String fullName = "com.mycache.MyCache";
        final PerpetualCache cache = new PerpetualCache(fullName);

        try {
            // 第一次添加缓存，预期成功。
            c.addCache(cache);
            // 第二次添加相同的缓存，预期抛出异常。
            c.addCache(cache);
            // 如果没有抛出异常，手动触发测试失败。
            fail("Exception expected.");
        } catch (Exception e) {
            // 捕获异常，验证异常消息是否包含特定文本。
            assertTrue(e.getMessage().contains("already contains key"));
        }
    }


    @Test
    void shouldOpenAndClose() {
        SqlSession session = sqlMapper.openSession(TransactionIsolationLevel.SERIALIZABLE);
        session.close();
    }

    @Test
    void shouldCommitAnUnUsedSqlSession() {
        try (SqlSession session = sqlMapper.openSession(TransactionIsolationLevel.SERIALIZABLE)) {
            session.commit(true);
        }
    }

    @Test
    void shouldRollbackAnUnUsedSqlSession() {
        try (SqlSession session = sqlMapper.openSession(TransactionIsolationLevel.SERIALIZABLE)) {
            session.rollback(true);
        }
    }

    @Test
    void shouldSelectAllAuthors() {
        try (SqlSession session = sqlMapper.openSession(TransactionIsolationLevel.SERIALIZABLE)) {
            List<Author> authors = session.selectList("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAllAuthors");
            assertEquals(2, authors.size());
        }
    }

    @Test
    void shouldFailWithTooManyResultsException() {
        try (SqlSession session = sqlMapper.openSession(TransactionIsolationLevel.SERIALIZABLE)) {
            Assertions.assertThrows(TooManyResultsException.class, () -> {
                session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAllAuthors");
            });
        }
    }

    @Test
    void shouldSelectAllAuthorsAsMap() {
        try (SqlSession session = sqlMapper.openSession(TransactionIsolationLevel.SERIALIZABLE)) {
            final Map<Integer, Author> authors = session
                .selectMap("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAllAuthors", "id");
            assertEquals(2, authors.size());
            for (Map.Entry<Integer, Author> authorEntry : authors.entrySet()) {
                assertEquals(authorEntry.getKey(), (Integer) authorEntry.getValue().getId());
            }
        }
    }

    @Test
    void shouldSelectCountOfPosts() {
        try (SqlSession session = sqlMapper.openSession()) {
            Integer count = session.selectOne("org.apache.ibatis.domain.blog.mappers.BlogMapper.selectCountOfPosts");
            assertEquals(5, count.intValue());
        }
    }

    @Test
    void shouldEnsureThatBothEarlyAndLateResolutionOfNesteDiscriminatorsResolesToUseNestedResultSetHandler() {
        Configuration configuration = sqlMapper.getConfiguration();
        assertTrue(
            configuration.getResultMap("org.apache.ibatis.domain.blog.mappers.BlogMapper.earlyNestedDiscriminatorPost")
                .hasNestedResultMaps());
        assertTrue(
            configuration.getResultMap("org.apache.ibatis.domain.blog.mappers.BlogMapper.lateNestedDiscriminatorPost")
                .hasNestedResultMaps());
    }

    @Test
    void shouldSelectOneAuthor() {
        try (SqlSession session = sqlMapper.openSession()) {
            Author author = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor",
                new Author(101));
            assertEquals(101, author.getId());
            assertEquals(Section.NEWS, author.getFavouriteSection());
        }
    }

    @Test
    void shouldSelectOneAuthorAsList() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Author> authors = session.selectList("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor",
                new Author(101));
            assertEquals(101, authors.get(0).getId());
            assertEquals(Section.NEWS, authors.get(0).getFavouriteSection());
        }
    }

    @Test
    void shouldSelectOneImmutableAuthor() {
        try (SqlSession session = sqlMapper.openSession()) {
            ImmutableAuthor author = session
                .selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectImmutableAuthor", new Author(101));
            assertEquals(101, author.getId());
            assertEquals(Section.NEWS, author.getFavouriteSection());
        }
    }

    @Test
    void shouldSelectOneAuthorWithInlineParams() {
        try (SqlSession session = sqlMapper.openSession()) {
            Author author = session.selectOne(
                "org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthorWithInlineParams", new Author(101));
            assertEquals(101, author.getId());
        }
    }

    @Test
    void shouldInsertAuthor() {
        try (SqlSession session = sqlMapper.openSession()) {
            Author expected = new Author(500, "cbegin", "******", "cbegin@somewhere.com", "Something...", null);
            int updates = session.insert("org.apache.ibatis.domain.blog.mappers.AuthorMapper.insertAuthor", expected);
            assertEquals(1, updates);
            Author actual = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor",
                new Author(500));
            assertNotNull(actual);
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getUsername(), actual.getUsername());
            assertEquals(expected.getPassword(), actual.getPassword());
            assertEquals(expected.getEmail(), actual.getEmail());
            assertEquals(expected.getBio(), actual.getBio());
        }
    }

    @Test
    void shouldUpdateAuthorImplicitRollback() {
        try (SqlSession session = sqlMapper.openSession()) {
            Author original = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            original.setEmail("new@email.com");
            int updates = session.update("org.apache.ibatis.domain.blog.mappers.AuthorMapper.updateAuthor", original);
            assertEquals(1, updates);
            Author updated = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            assertEquals(original.getEmail(), updated.getEmail());
        }
        try (SqlSession session = sqlMapper.openSession()) {
            Author updated = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            assertEquals("jim@ibatis.apache.org", updated.getEmail());
        }
    }

    @Test
    void shouldUpdateAuthorCommit() {
        Author original;
        try (SqlSession session = sqlMapper.openSession()) {
            original = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            original.setEmail("new@email.com");
            int updates = session.update("org.apache.ibatis.domain.blog.mappers.AuthorMapper.updateAuthor", original);
            assertEquals(1, updates);
            Author updated = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            assertEquals(original.getEmail(), updated.getEmail());
            session.commit();
        }
        try (SqlSession session = sqlMapper.openSession()) {
            Author updated = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            assertEquals(original.getEmail(), updated.getEmail());
        }
    }

    @Test
    void shouldUpdateAuthorIfNecessary() {
        Author original;
        try (SqlSession session = sqlMapper.openSession()) {
            original = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            original.setEmail("new@email.com");
            original.setBio(null);
            int updates = session.update("org.apache.ibatis.domain.blog.mappers.AuthorMapper.updateAuthorIfNecessary",
                original);
            assertEquals(1, updates);
            Author updated = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            assertEquals(original.getEmail(), updated.getEmail());
            session.commit();
        }
        try (SqlSession session = sqlMapper.openSession()) {
            Author updated = session.selectOne("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", 101);
            assertEquals(original.getEmail(), updated.getEmail());
        }
    }

    @Test
    void shouldDeleteAuthor() {
        try (SqlSession session = sqlMapper.openSession()) {
            final int id = 102;

            List<Author> authors = session.selectList("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", id);
            assertEquals(1, authors.size());

            int updates = session.delete("org.apache.ibatis.domain.blog.mappers.AuthorMapper.deleteAuthor", id);
            assertEquals(1, updates);

            authors = session.selectList("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", id);
            assertEquals(0, authors.size());

            session.rollback();
            authors = session.selectList("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor", id);
            assertEquals(1, authors.size());
        }
    }

    @Test
    void shouldSelectBlogWithPostsAndAuthorUsingSubSelects() {
        try (SqlSession session = sqlMapper.openSession()) {
            Blog blog = session
                .selectOne("org.apache.ibatis.domain.blog.mappers.BlogMapper.selectBlogWithPostsUsingSubSelect", 1);
            assertEquals("Jim Business", blog.getTitle());
            assertEquals(2, blog.getPosts().size());
            assertEquals("Corn nuts", blog.getPosts().get(0).getSubject());
            assertEquals(101, blog.getAuthor().getId());
            assertEquals("jim", blog.getAuthor().getUsername());
        }
    }

    @Test
    void shouldSelectBlogWithPostsAndAuthorUsingSubSelectsLazily() {
        try (SqlSession session = sqlMapper.openSession()) {
            Blog blog = session
                .selectOne("org.apache.ibatis.domain.blog.mappers.BlogMapper.selectBlogWithPostsUsingSubSelectLazily", 1);
            Assertions.assertTrue(blog instanceof Proxy);
            assertEquals("Jim Business", blog.getTitle());
            assertEquals(2, blog.getPosts().size());
            assertEquals("Corn nuts", blog.getPosts().get(0).getSubject());
            assertEquals(101, blog.getAuthor().getId());
            assertEquals("jim", blog.getAuthor().getUsername());
        }
    }

    @Test
    void shouldSelectBlogWithPostsAndAuthorUsingJoin() {
        try (SqlSession session = sqlMapper.openSession()) {
            Blog blog = session
                .selectOne("org.apache.ibatis.domain.blog.mappers.BlogMapper.selectBlogJoinedWithPostsAndAuthor", 1);
            assertEquals("Jim Business", blog.getTitle());

            final Author author = blog.getAuthor();
            assertEquals(101, author.getId());
            assertEquals("jim", author.getUsername());

            final List<Post> posts = blog.getPosts();
            assertEquals(2, posts.size());

            final Post post = blog.getPosts().get(0);
            assertEquals(1, post.getId());
            assertEquals("Corn nuts", post.getSubject());

            final List<Comment> comments = post.getComments();
            assertEquals(2, comments.size());

            final List<Tag> tags = post.getTags();
            assertEquals(3, tags.size());

            final Comment comment = comments.get(0);
            assertEquals(1, comment.getId());

            assertEquals(DraftPost.class, blog.getPosts().get(0).getClass());
            assertEquals(Post.class, blog.getPosts().get(1).getClass());
        }
    }

    @Test
    void shouldSelectNestedBlogWithPostsAndAuthorUsingJoin() {
        try (SqlSession session = sqlMapper.openSession()) {
            Blog blog = session
                .selectOne("org.apache.ibatis.domain.blog.mappers.NestedBlogMapper.selectBlogJoinedWithPostsAndAuthor", 1);
            assertEquals("Jim Business", blog.getTitle());

            final Author author = blog.getAuthor();
            assertEquals(101, author.getId());
            assertEquals("jim", author.getUsername());

            final List<Post> posts = blog.getPosts();
            assertEquals(2, posts.size());

            final Post post = blog.getPosts().get(0);
            assertEquals(1, post.getId());
            assertEquals("Corn nuts", post.getSubject());

            final List<Comment> comments = post.getComments();
            assertEquals(2, comments.size());

            final List<Tag> tags = post.getTags();
            assertEquals(3, tags.size());

            final Comment comment = comments.get(0);
            assertEquals(1, comment.getId());

            assertEquals(DraftPost.class, blog.getPosts().get(0).getClass());
            assertEquals(Post.class, blog.getPosts().get(1).getClass());
        }
    }

    @Test
    void shouldThrowExceptionIfMappedStatementDoesNotExist() {
        try (SqlSession session = sqlMapper.openSession()) {
            session.selectList("ThisStatementDoesNotExist");
            fail("Expected exception to be thrown due to statement that does not exist.");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("does not contain value for ThisStatementDoesNotExist"));
        }
    }

    @Test
    void shouldThrowExceptionIfTryingToAddStatementWithSameNameInXml() {
        Configuration config = sqlMapper.getConfiguration();
        try {
            MappedStatement ms = new MappedStatement.Builder(config,
                "org.apache.ibatis.domain.blog.mappers.BlogMapper.selectBlogWithPostsUsingSubSelect",
                Mockito.mock(SqlSource.class), SqlCommandType.SELECT).resource("org/mybatis/TestMapper.xml").build();
            config.addMappedStatement(ms);
            fail("Expected exception to be thrown due to statement that already exists.");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains(
                "already contains key org.apache.ibatis.domain.blog.mappers.BlogMapper.selectBlogWithPostsUsingSubSelect. please check org/apache/ibatis/builder/BlogMapper.xml and org/mybatis/TestMapper.xml"));
        }
    }

    @Test
    void shouldThrowExceptionIfTryingToAddStatementWithSameNameInAnnotation() {
        Configuration config = sqlMapper.getConfiguration();
        try {
            MappedStatement ms = new MappedStatement.Builder(config,
                "org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor2", Mockito.mock(SqlSource.class),
                SqlCommandType.SELECT).resource("org/mybatis/TestMapper.xml").build();
            config.addMappedStatement(ms);
            fail("Expected exception to be thrown due to statement that already exists.");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains(
                "already contains key org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAuthor2. please check org/apache/ibatis/domain/blog/mappers/AuthorMapper.java (best guess) and org/mybatis/TestMapper.xml"));
        }
    }

    @Test
    void shouldCacheAllAuthors() {
        int first;
        try (SqlSession session = sqlMapper.openSession()) {
            List<Author> authors = session.selectList("org.apache.ibatis.builder.CachedAuthorMapper.selectAllAuthors");
            first = System.identityHashCode(authors);
            session.commit(); // commit should not be required for read/only activity.
        }
        int second;
        try (SqlSession session = sqlMapper.openSession()) {
            List<Author> authors = session.selectList("org.apache.ibatis.builder.CachedAuthorMapper.selectAllAuthors");
            second = System.identityHashCode(authors);
        }
        assertEquals(first, second);
    }

    @Test
    void shouldNotCacheAllAuthors() {
        int first;
        try (SqlSession session = sqlMapper.openSession()) {
            List<Author> authors = session.selectList("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAllAuthors");
            first = System.identityHashCode(authors);
        }
        int second;
        try (SqlSession session = sqlMapper.openSession()) {
            List<Author> authors = session.selectList("org.apache.ibatis.domain.blog.mappers.AuthorMapper.selectAllAuthors");
            second = System.identityHashCode(authors);
        }
        assertTrue(first != second);
    }

    @Test
    void shouldSelectAuthorsUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            List<Author> authors = mapper.selectAllAuthors();
            assertEquals(2, authors.size());
        }
    }

    @Test
    void shouldExecuteSelectOneAuthorUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Author author = mapper.selectAuthor(101);
            assertEquals(101, author.getId());
        }
    }

    @Test
    void shouldExecuteSelectOneAuthorUsingMapperClassThatReturnsALinedHashMap() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            LinkedHashMap<String, Object> author = mapper.selectAuthorLinkedHashMap(101);
            assertEquals(101, author.get("ID"));
        }
    }

    @Test
    void shouldExecuteSelectAllAuthorsUsingMapperClassThatReturnsSet() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Collection<Author> authors = mapper.selectAllAuthorsSet();
            assertEquals(2, authors.size());
        }
    }

    @Test
    void shouldExecuteSelectAllAuthorsUsingMapperClassThatReturnsVector() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Collection<Author> authors = mapper.selectAllAuthorsVector();
            assertEquals(2, authors.size());
        }
    }

    @Test
    void shouldExecuteSelectAllAuthorsUsingMapperClassThatReturnsLinkedList() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Collection<Author> authors = mapper.selectAllAuthorsLinkedList();
            assertEquals(2, authors.size());
        }
    }

    @Test
    void shouldExecuteSelectAllAuthorsUsingMapperClassThatReturnsAnArray() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Author[] authors = mapper.selectAllAuthorsArray();
            assertEquals(2, authors.length);
        }
    }

    @Test
    void shouldExecuteSelectOneAuthorUsingMapperClassWithResultHandler() {
        try (SqlSession session = sqlMapper.openSession()) {
            DefaultResultHandler handler = new DefaultResultHandler();
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            mapper.selectAuthor(101, handler);
            Author author = (Author) handler.getResultList().get(0);
            assertEquals(101, author.getId());
        }
    }

    @Test
    void shouldFailExecutingAnAnnotatedMapperClassWithResultHandler() {
        try (SqlSession session = sqlMapper.openSession()) {
            DefaultResultHandler handler = new DefaultResultHandler();
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Assertions.assertThrows(BindingException.class, () -> {
                mapper.selectAuthor2(101, handler);
            });
        }
    }

    @Test
    void shouldSelectAuthorsUsingMapperClassWithResultHandler() {
        try (SqlSession session = sqlMapper.openSession()) {
            DefaultResultHandler handler = new DefaultResultHandler();
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            mapper.selectAllAuthors(handler);
            assertEquals(2, handler.getResultList().size());
        }
    }

    @Test
    void shouldFailSelectOneAuthorUsingMapperClassWithTwoResultHandlers() {
        Configuration configuration = new Configuration(sqlMapper.getConfiguration().getEnvironment());
        configuration.addMapper(AuthorMapperWithMultipleHandlers.class);
        SqlSessionFactory sqlMapperWithMultipleHandlers = new DefaultSqlSessionFactory(configuration);
        try (SqlSession sqlSession = sqlMapperWithMultipleHandlers.openSession();) {
            DefaultResultHandler handler1 = new DefaultResultHandler();
            DefaultResultHandler handler2 = new DefaultResultHandler();
            AuthorMapperWithMultipleHandlers mapper = sqlSession.getMapper(AuthorMapperWithMultipleHandlers.class);
            Assertions.assertThrows(BindingException.class, () -> mapper.selectAuthor(101, handler1, handler2));
        }
    }

    @Test
    void shouldFailSelectOneAuthorUsingMapperClassWithTwoRowBounds() {
        Configuration configuration = new Configuration(sqlMapper.getConfiguration().getEnvironment());
        configuration.addMapper(AuthorMapperWithRowBounds.class);
        SqlSessionFactory sqlMapperWithMultipleHandlers = new DefaultSqlSessionFactory(configuration);
        try (SqlSession sqlSession = sqlMapperWithMultipleHandlers.openSession();) {
            RowBounds bounds1 = new RowBounds(0, 1);
            RowBounds bounds2 = new RowBounds(0, 1);
            AuthorMapperWithRowBounds mapper = sqlSession.getMapper(AuthorMapperWithRowBounds.class);
            Assertions.assertThrows(BindingException.class, () -> mapper.selectAuthor(101, bounds1, bounds2));
        }
    }

    @Test
    void shouldInsertAuthorUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Author expected = new Author(500, "cbegin", "******", "cbegin@somewhere.com", "Something...", null);
            mapper.insertAuthor(expected);
            Author actual = mapper.selectAuthor(500);
            assertNotNull(actual);
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getUsername(), actual.getUsername());
            assertEquals(expected.getPassword(), actual.getPassword());
            assertEquals(expected.getEmail(), actual.getEmail());
            assertEquals(expected.getBio(), actual.getBio());
        }
    }

    @Test
    void shouldDeleteAuthorUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            int count = mapper.deleteAuthor(101);
            assertEquals(1, count);
            assertNull(mapper.selectAuthor(101));
        }
    }

    @Test
    void shouldUpdateAuthorUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            AuthorMapper mapper = session.getMapper(AuthorMapper.class);
            Author expected = mapper.selectAuthor(101);
            expected.setUsername("NewUsername");
            int count = mapper.updateAuthor(expected);
            assertEquals(1, count);
            Author actual = mapper.selectAuthor(101);
            assertEquals(expected.getUsername(), actual.getUsername());
        }
    }

    @Test
    void shouldSelectAllPostsUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            BlogMapper mapper = session.getMapper(BlogMapper.class);
            List<Map> posts = mapper.selectAllPosts();
            assertEquals(5, posts.size());
        }
    }

    @Test
    void shouldLimitResultsUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            BlogMapper mapper = session.getMapper(BlogMapper.class);
            List<Map> posts = mapper.selectAllPosts(new RowBounds(0, 2), null);
            assertEquals(2, posts.size());
            assertEquals(1, posts.get(0).get("ID"));
            assertEquals(2, posts.get(1).get("ID"));
        }
    }

    private static class TestResultHandler implements ResultHandler {
        int count;

        @Override
        public void handleResult(ResultContext context) {
            count++;
        }
    }

    @Test
    void shouldHandleZeroParameters() {
        try (SqlSession session = sqlMapper.openSession()) {
            final TestResultHandler resultHandler = new TestResultHandler();
            session.select("org.apache.ibatis.domain.blog.mappers.BlogMapper.selectAllPosts", resultHandler);
            assertEquals(5, resultHandler.count);
        }
    }

    private static class TestResultStopHandler implements ResultHandler {
        int count;

        @Override
        public void handleResult(ResultContext context) {
            count++;
            if (count == 2) {
                context.stop();
            }
        }
    }

    @Test
    void shouldStopResultHandler() {
        try (SqlSession session = sqlMapper.openSession()) {
            final TestResultStopHandler resultHandler = new TestResultStopHandler();
            session.select("org.apache.ibatis.domain.blog.mappers.BlogMapper.selectAllPosts", null, resultHandler);
            assertEquals(2, resultHandler.count);
        }
    }

    @Test
    void shouldOffsetAndLimitResultsUsingMapperClass() {
        try (SqlSession session = sqlMapper.openSession()) {
            BlogMapper mapper = session.getMapper(BlogMapper.class);
            List<Map> posts = mapper.selectAllPosts(new RowBounds(2, 3));
            assertEquals(3, posts.size());
            assertEquals(3, posts.get(0).get("ID"));
            assertEquals(4, posts.get(1).get("ID"));
            assertEquals(5, posts.get(2).get("ID"));
        }
    }

    @Test
    void shouldFindPostsAllPostsWithDynamicSql() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.findPost");
            assertEquals(5, posts.size());
        }
    }

    @Test
    void shouldFindPostByIDWithDynamicSql() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.findPost",
                new HashMap<String, Integer>() {
                    private static final long serialVersionUID = 1L;

                    {
                        put("id", 1);
                    }
                });
            assertEquals(1, posts.size());
        }
    }

    @Test
    void shouldFindPostsInSetOfIDsWithDynamicSql() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.findPost",
                new HashMap<String, List<Integer>>() {
                    private static final long serialVersionUID = 1L;

                    {
                        put("ids", new ArrayList<Integer>() {
                            private static final long serialVersionUID = 1L;

                            {
                                add(1);
                                add(2);
                                add(3);
                            }
                        });
                    }
                });
            assertEquals(3, posts.size());
        }
    }

    @Test
    void shouldFindPostsWithBlogIdUsingDynamicSql() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.findPost",
                new HashMap<String, Integer>() {
                    private static final long serialVersionUID = 1L;

                    {
                        put("blog_id", 1);
                    }
                });
            assertEquals(2, posts.size());
        }
    }

    @Test
    void shouldFindPostsWithAuthorIdUsingDynamicSql() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.findPost",
                new HashMap<String, Integer>() {
                    private static final long serialVersionUID = 1L;

                    {
                        put("author_id", 101);
                    }
                });
            assertEquals(3, posts.size());
        }
    }

    @Test
    void shouldFindPostsWithAuthorAndBlogIdUsingDynamicSql() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.findPost",
                new HashMap<String, Object>() {
                    private static final long serialVersionUID = 1L;

                    {
                        put("ids", new ArrayList<Integer>() {
                            private static final long serialVersionUID = 1L;

                            {
                                add(1);
                                add(2);
                                add(3);
                            }
                        });
                        put("blog_id", 1);
                    }
                });
            assertEquals(2, posts.size());
        }
    }

    @Test
    void shouldFindPostsInList() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.selectPostIn",
                new ArrayList<Integer>() {
                    private static final long serialVersionUID = 1L;

                    {
                        add(1);
                        add(3);
                        add(5);
                    }
                });
            assertEquals(3, posts.size());
        }
    }

    @Test
    void shouldFindOddPostsInList() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.selectOddPostsIn",
                new ArrayList<Integer>() {
                    private static final long serialVersionUID = 1L;

                    {
                        add(0);
                        add(1);
                        add(2);
                        add(3);
                        add(4);
                    }
                });
            // we're getting odd indexes, not odd values, 0 is not odd
            assertEquals(2, posts.size());
            assertEquals(1, posts.get(0).getId());
            assertEquals(3, posts.get(1).getId());
        }
    }

    @Test
    void shouldSelectOddPostsInKeysList() {
        try (SqlSession session = sqlMapper.openSession()) {
            List<Post> posts = session.selectList("org.apache.ibatis.domain.blog.mappers.PostMapper.selectOddPostsInKeysList",
                new HashMap<String, List<Integer>>() {
                    private static final long serialVersionUID = 1L;

                    {
                        put("keys", new ArrayList<Integer>() {
                            private static final long serialVersionUID = 1L;

                            {
                                add(0);
                                add(1);
                                add(2);
                                add(3);
                                add(4);
                            }
                        });
                    }
                });
            // we're getting odd indexes, not odd values, 0 is not odd
            assertEquals(2, posts.size());
            assertEquals(1, posts.get(0).getId());
            assertEquals(3, posts.get(1).getId());
        }
    }

}
