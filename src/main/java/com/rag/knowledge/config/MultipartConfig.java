package com.rag.knowledge.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class MultipartConfig {

    private final UploadProperties uploadProperties;

    public MultipartConfig(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        DataSize maxSize = DataSize.ofMegabytes(uploadProperties.getMaxSizeMb());
        factory.setMaxFileSize(maxSize);
        factory.setMaxRequestSize(maxSize);
        return factory.createMultipartConfig();
    }
}
