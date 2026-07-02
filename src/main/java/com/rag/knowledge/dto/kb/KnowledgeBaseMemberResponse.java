package com.rag.knowledge.dto.kb;

import java.time.LocalDateTime;

public record KnowledgeBaseMemberResponse(
        Long id,
        Long userId,
        String username,
        String role,
        boolean owner,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
