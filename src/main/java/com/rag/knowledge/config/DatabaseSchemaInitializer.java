package com.rag.knowledge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        createUserTable();
        createKnowledgeBaseTable();
        createDocumentTable();
        log.info("Database schema checked");
    }

    private void createUserTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `user` (
                  `id` BIGINT NOT NULL PRIMARY KEY,
                  `username` VARCHAR(64) NOT NULL,
                  `password` VARCHAR(255) NOT NULL,
                  `role` VARCHAR(32) NOT NULL DEFAULT 'USER',
                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY `uk_user_username` (`username`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createKnowledgeBaseTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `knowledge_base` (
                  `id` BIGINT NOT NULL PRIMARY KEY,
                  `user_id` BIGINT NOT NULL,
                  `name` VARCHAR(128) NOT NULL,
                  `description` VARCHAR(512) DEFAULT NULL,
                  `visibility` VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY `idx_kb_user_id` (`user_id`),
                  UNIQUE KEY `uk_kb_user_name` (`user_id`, `name`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createDocumentTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `document` (
                  `id` BIGINT NOT NULL PRIMARY KEY,
                  `user_id` BIGINT NOT NULL,
                  `kb_id` BIGINT NOT NULL,
                  `file_name` VARCHAR(255) NOT NULL,
                  `file_type` VARCHAR(32) NOT NULL,
                  `file_url` VARCHAR(512) NOT NULL,
                  `file_size` BIGINT NOT NULL,
                  `status` VARCHAR(32) NOT NULL,
                  `error_msg` TEXT DEFAULT NULL,
                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY `idx_doc_user_kb` (`user_id`, `kb_id`),
                  KEY `idx_doc_status` (`status`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }
}
