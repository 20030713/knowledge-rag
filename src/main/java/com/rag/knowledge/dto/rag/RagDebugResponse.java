package com.rag.knowledge.dto.rag;

import java.util.List;

public record RagDebugResponse(
        Long kbId,
        String question,
        Integer topK,
        Double vectorWeight,
        Double keywordWeight,
        Boolean cacheHit,
        String answerMode,
        String vectorBackend,
        Long latencyMs,
        List<String> queryTerms,
        List<RagDebugChunkResponse> chunks
) {
}
