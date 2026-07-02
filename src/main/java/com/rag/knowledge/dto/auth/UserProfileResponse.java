package com.rag.knowledge.dto.auth;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long userId,
        String username,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<UserLoginLogResponse> recentLogins
) {
}
