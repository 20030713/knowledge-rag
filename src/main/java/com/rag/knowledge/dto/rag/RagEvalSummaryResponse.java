package com.rag.knowledge.dto.rag;

import java.util.List;

public record RagEvalSummaryResponse(
        Long kbId,
        Integer totalCount,
        Integer passedCount,
        Double passRate,
        Double averageKeywordScore,
        Long averageLatencyMs,
        List<RagEvalRunResponse> runs
) {
}
