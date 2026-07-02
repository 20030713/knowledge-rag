package com.rag.knowledge.dto.admin;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long userId,
        String username,
        String role,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt,
        Boolean lastLoginSuccess
) {
}
