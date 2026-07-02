package com.rag.knowledge.dto.auth;

import java.time.LocalDateTime;

public record UserLoginLogResponse(
        Long id,
        String ipAddress,
        String userAgent,
        Boolean success,
        String message,
        LocalDateTime createdAt
) {
}
