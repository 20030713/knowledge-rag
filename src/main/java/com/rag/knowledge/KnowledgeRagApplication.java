package com.rag.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.rag.knowledge.repository")
@SpringBootApplication
public class KnowledgeRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeRagApplication.class, args);
    }
}
