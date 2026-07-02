package com.rag.knowledge.dto.rag;

import java.time.LocalDateTime;
import java.util.List;

public record QaRecordResponse(
        Long id,
        Long kbId,
        Long sessionId,
        String question,
        String answer,
        Integer hitCount,
        List<RagCitationResponse> citations,
        String answerStyle,
        String answerSource,
        String modelName,
        Long latencyMs,
        Boolean fallback,
        Integer feedbackScore,
        String feedbackNote,
        LocalDateTime feedbackAt,
        LocalDateTime createdAt
) {
}
