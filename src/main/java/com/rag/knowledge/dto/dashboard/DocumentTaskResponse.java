package com.rag.knowledge.dto.dashboard;

import java.time.LocalDateTime;

public record DocumentTaskResponse(
        Long id,
        Long kbId,
        String fileName,
        String status,
        String errorMsg,
        Integer retryCount,
        Integer chunkCount,
        Long parseDurationMs,
        String queueStatus,
        Integer queueRetryCount,
        String queueErrorMsg,
        LocalDateTime queueAvailableAt,
        LocalDateTime queueStartedAt,
        LocalDateTime queueFinishedAt,
        LocalDateTime updatedAt
) {
}
