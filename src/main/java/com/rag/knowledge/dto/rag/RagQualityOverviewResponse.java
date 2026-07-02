package com.rag.knowledge.dto.rag;

public record RagQualityOverviewResponse(
        Long kbId,
        Integer totalCount,
        Integer feedbackCount,
        Integer helpfulCount,
        Integer unhelpfulCount,
        Integer noFeedbackCount,
        Integer modelAnswerCount,
        Integer localAnswerCount,
        Integer fallbackCount,
        Integer noCitationCount,
        Integer highLatencyCount,
        Long averageLatencyMs,
        Integer qualityScore
) {
}
