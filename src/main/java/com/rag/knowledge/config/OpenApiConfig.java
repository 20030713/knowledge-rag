package com.rag.knowledge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowledgeRagOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("企业知识库 RAG 问答系统")
                        .description("Spring Boot 后端接口文档")
                        .version("0.0.1"));
    }
}
