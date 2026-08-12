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
        int chunkSize,
        int chunkOverlap,
        int minBreakSize,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
