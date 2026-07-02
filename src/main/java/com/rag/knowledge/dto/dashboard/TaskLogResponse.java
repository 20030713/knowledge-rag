package com.rag.knowledge.dto.dashboard;

import java.time.LocalDateTime;

public record TaskLogResponse(
        Long id,
        Long documentId,
        String taskType,
        String status,
        String message,
        Long durationMs,
        LocalDateTime createdAt
) {
}
