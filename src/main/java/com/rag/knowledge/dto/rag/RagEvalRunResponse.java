package com.rag.knowledge.dto.rag;

import java.time.LocalDateTime;

public record RagEvalRunResponse(
        Long id,
        Long caseId,
        String question,
        String answer,
        Integer hitCount,
        Double keywordScore,
        Boolean noAnswerCase,
        Boolean retrievalHit,
        Double reciprocalRank,
        Double citationPrecision,
        Boolean abstentionCorrect,
        Boolean passed,
        Long latencyMs,
        LocalDateTime createdAt
) {
}
