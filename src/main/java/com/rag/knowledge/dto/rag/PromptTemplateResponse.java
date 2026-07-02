package com.rag.knowledge.dto.rag;

import java.time.LocalDateTime;

public record PromptTemplateResponse(
        Long id,
        Long kbId,
        String name,
        String answerStyle,
        String systemPrompt,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
