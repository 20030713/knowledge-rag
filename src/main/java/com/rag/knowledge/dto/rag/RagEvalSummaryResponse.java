package com.rag.knowledge.dto.rag;

import java.util.List;

public record RagEvalSummaryResponse(
        Long kbId,
        Integer totalCount,
        Integer passedCount,
        Double passRate,
        Double averageKeywordScore,
        Double retrievalHitRate,
        Double meanReciprocalRank,
        Double averageCitationPrecision,
        Double abstentionAccuracy,
        Long averageLatencyMs,
        List<RagEvalRunResponse> runs
) {
}
