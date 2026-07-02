package com.rag.knowledge.dto.dashboard;

public record TodayMetricsResponse(
        Integer qaCount,
        Integer modelAnswerCount,
        Integer localAnswerCount,
        Integer fallbackCount,
        Integer highLatencyCount,
        Long averageLatencyMs,
        Integer modelSuccessRate,
        Boolean cacheEnabled,
        Integer cacheTtlMinutes,
        Boolean rateLimitEnabled,
        Integer ragAskLimit,
        Integer ragAskWindowSeconds
) {
}
