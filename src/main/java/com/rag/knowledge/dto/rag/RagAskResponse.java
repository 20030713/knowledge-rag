package com.rag.knowledge.dto.rag;

import java.util.List;

public record RagAskResponse(
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
        Boolean cacheHit,
        Boolean fallback
) {
}
