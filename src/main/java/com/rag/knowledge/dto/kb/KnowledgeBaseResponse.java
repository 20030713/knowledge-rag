package com.rag.knowledge.dto.kb;

import java.time.LocalDateTime;

public record KnowledgeBaseResponse(
        Long id,
        Long ownerUserId,
        String ownerUsername,
        String name,
        String description,
        String visibility,
        String accessRole,
        boolean owned,
        int memberCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
