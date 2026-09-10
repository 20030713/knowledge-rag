package com.rag.knowledge.dto.rag;

import java.time.LocalDateTime;

public record RagEvalCaseResponse(
        Long id,
        Long kbId,
        String question,
        String expectedAnswer,
        String expectedKeywords,
        String expectedSource,
        Boolean expectNoAnswer,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
