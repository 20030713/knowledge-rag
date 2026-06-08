package com.rag.knowledge.dto.kb;

import java.time.LocalDateTime;

public record KnowledgeBaseResponse(
        Long id,
        String name,
        String description,
        String visibility,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
