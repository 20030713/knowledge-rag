package com.rag.knowledge;

import com.rag.knowledge.job.DocumentTaskQueueWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "app.document-task.worker-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class KnowledgeRagApplicationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("knowledge_rag")
            .withUsername("knowledge_rag")
            .withPassword("knowledge_rag_test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithIsolatedDatabaseAndDisabledWorker() {
        Integer knowledgeBaseCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_base", Integer.class);

        org.assertj.core.api.Assertions.assertThat(knowledgeBaseCount).isZero();
        org.assertj.core.api.Assertions.assertThat(applicationContext.getBeansOfType(DocumentTaskQueueWorker.class)).isEmpty();
    }
}
