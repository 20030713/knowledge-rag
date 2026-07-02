package com.rag.knowledge.dto.rag;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long sessionId,
        String role,
        String content,
        LocalDateTime createdAt
) {
}
