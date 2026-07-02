CREATE DATABASE IF NOT EXISTS knowledge_rag
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE knowledge_rag;

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `username` VARCHAR(64) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_login_log` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT DEFAULT NULL,
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `user_agent` VARCHAR(512) DEFAULT NULL,
  `success` TINYINT(1) NOT NULL DEFAULT 1,
  `message` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_login_log_user_time` (`user_id`, `created_at`),
  KEY `idx_login_log_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `admin_operation_log` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `admin_user_id` BIGINT NOT NULL,
  `target_user_id` BIGINT DEFAULT NULL,
  `action` VARCHAR(64) NOT NULL,
  `result` VARCHAR(32) NOT NULL,
  `detail` VARCHAR(1000) DEFAULT NULL,
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `user_agent` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_admin_op_admin_time` (`admin_user_id`, `created_at`),
  KEY `idx_admin_op_target_time` (`target_user_id`, `created_at`),
  KEY `idx_admin_op_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `knowledge_base_member` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `kb_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` VARCHAR(32) NOT NULL DEFAULT 'VIEWER',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_kb_member` (`kb_id`, `user_id`),
  KEY `idx_kb_member_user` (`user_id`),
  KEY `idx_kb_member_kb` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_preference` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `default_answer_style` VARCHAR(32) NOT NULL DEFAULT 'STRICT',
  `default_top_k` INT NOT NULL DEFAULT 5,
  `vector_weight` DOUBLE NOT NULL DEFAULT 0.7,
  `keyword_weight` DOUBLE NOT NULL DEFAULT 0.3,
  `enable_model` TINYINT(1) NOT NULL DEFAULT 1,
  `enable_cache` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_preference_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `title` VARCHAR(128) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_chat_session_user_kb` (`user_id`, `kb_id`),
  KEY `idx_chat_session_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `session_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `role` VARCHAR(32) NOT NULL,
  `content` LONGTEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_chat_message_session_time` (`session_id`, `created_at`),
  KEY `idx_chat_message_user_kb` (`user_id`, `kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
  `retry_count` INT NOT NULL DEFAULT 0,
  `chunk_count` INT NOT NULL DEFAULT 0,
  `parse_duration_ms` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_doc_user_kb` (`user_id`, `kb_id`),
  KEY `idx_doc_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `document_chunk` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `document_id` BIGINT NOT NULL,
  `chunk_no` INT NOT NULL,
  `content` TEXT NOT NULL,
  `char_count` INT NOT NULL,
  `vector_id` VARCHAR(128) DEFAULT NULL,
  `embedding_model` VARCHAR(256) DEFAULT NULL,
  `embedding_json` LONGTEXT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_chunk_document_id` (`document_id`),
  KEY `idx_chunk_user_kb` (`user_id`, `kb_id`),
  UNIQUE KEY `uk_chunk_document_no` (`document_id`, `chunk_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `task_log` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `document_id` BIGINT NOT NULL,
  `task_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `message` VARCHAR(1000) NOT NULL,
  `duration_ms` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_task_log_user_doc` (`user_id`, `document_id`),
  KEY `idx_task_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `document_task_queue` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `document_id` BIGINT NOT NULL,
  `task_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT NOT NULL DEFAULT 0,
  `error_msg` VARCHAR(500) DEFAULT NULL,
  `available_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` DATETIME DEFAULT NULL,
  `finished_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_doc_task_queue_pick` (`task_type`, `status`, `available_at`, `created_at`),
  KEY `idx_doc_task_queue_doc` (`document_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `qa_record` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `session_id` BIGINT DEFAULT NULL,
  `question` VARCHAR(500) NOT NULL,
  `answer` TEXT NOT NULL,
  `hit_count` INT NOT NULL DEFAULT 0,
  `citations_json` LONGTEXT DEFAULT NULL,
  `answer_style` VARCHAR(32) NOT NULL DEFAULT 'STRICT',
  `answer_source` VARCHAR(32) NOT NULL DEFAULT 'LOCAL_FALLBACK',
  `model_name` VARCHAR(128) DEFAULT NULL,
  `latency_ms` BIGINT DEFAULT NULL,
  `fallback` TINYINT(1) NOT NULL DEFAULT 0,
  `feedback_score` INT DEFAULT NULL,
  `feedback_note` VARCHAR(500) DEFAULT NULL,
  `feedback_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_qa_user_kb` (`user_id`, `kb_id`),
  KEY `idx_qa_session` (`session_id`),
  KEY `idx_qa_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `prompt_template` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `answer_style` VARCHAR(32) NOT NULL DEFAULT 'STRICT',
  `system_prompt` LONGTEXT NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_prompt_template_kb_style` (`kb_id`, `answer_style`, `enabled`),
  KEY `idx_prompt_template_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `qa_citation` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `qa_record_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `chunk_id` BIGINT NOT NULL,
  `document_id` BIGINT NOT NULL,
  `document_name` VARCHAR(255) NOT NULL,
  `chunk_no` INT NOT NULL,
  `rank_no` INT NOT NULL,
  `content` TEXT NOT NULL,
  `score` DOUBLE DEFAULT NULL,
  `vector_score` DOUBLE DEFAULT NULL,
  `keyword_score` DOUBLE DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_qa_citation_record` (`qa_record_id`, `rank_no`),
  KEY `idx_qa_citation_kb` (`kb_id`, `created_at`),
  KEY `idx_qa_citation_chunk` (`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rag_eval_case` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `question` VARCHAR(500) NOT NULL,
  `expected_answer` TEXT NOT NULL,
  `expected_keywords` VARCHAR(500) DEFAULT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_eval_case_kb` (`kb_id`, `enabled`, `updated_at`),
  KEY `idx_eval_case_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rag_eval_run` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `kb_id` BIGINT NOT NULL,
  `case_id` BIGINT NOT NULL,
  `question` VARCHAR(500) NOT NULL,
  `answer` TEXT NOT NULL,
  `hit_count` INT NOT NULL DEFAULT 0,
  `keyword_score` DOUBLE DEFAULT NULL,
  `passed` TINYINT(1) NOT NULL DEFAULT 0,
  `latency_ms` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_eval_run_kb_time` (`kb_id`, `created_at`),
  KEY `idx_eval_run_case_time` (`case_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
