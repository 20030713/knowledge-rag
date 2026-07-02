package com.rag.knowledge.dto.admin;

import java.time.LocalDateTime;

public record AdminOperationLogResponse(
        Long id,
        Long adminUserId,
        String adminUsername,
        Long targetUserId,
        String targetUsername,
        String action,
        String result,
        String detail,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {
}
