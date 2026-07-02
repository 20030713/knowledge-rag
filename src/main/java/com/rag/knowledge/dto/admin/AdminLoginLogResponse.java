package com.rag.knowledge.dto.admin;

import java.time.LocalDateTime;

public record AdminLoginLogResponse(
        Long id,
        Long userId,
        String username,
        String ipAddress,
        String userAgent,
        Boolean success,
        String message,
        LocalDateTime createdAt
) {
}
