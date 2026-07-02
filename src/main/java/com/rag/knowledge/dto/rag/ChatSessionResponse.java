package com.rag.knowledge.dto.rag;

import java.time.LocalDateTime;

public record ChatSessionResponse(
        Long id,
        Long kbId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
