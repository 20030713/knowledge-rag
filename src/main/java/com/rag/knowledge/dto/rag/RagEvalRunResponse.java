package com.rag.knowledge.dto.rag;

import java.time.LocalDateTime;

public record RagEvalRunResponse(
        Long id,
        Long caseId,
        String question,
        String answer,
        Integer hitCount,
        Double keywordScore,
        Boolean passed,
        Long latencyMs,
        LocalDateTime createdAt
) {
}
