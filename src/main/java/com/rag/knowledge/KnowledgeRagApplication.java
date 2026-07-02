package com.rag.knowledge;

import com.rag.knowledge.config.RateLimitProperties;
import com.rag.knowledge.config.RagCacheProperties;
import com.rag.knowledge.config.EmbeddingModelProperties;
import com.rag.knowledge.config.PgVectorProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.rag.knowledge.repository")
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({
        RateLimitProperties.class,
        RagCacheProperties.class,
        EmbeddingModelProperties.class,
        PgVectorProperties.class
})
public class KnowledgeRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeRagApplication.class, args);
    }
}
