package com.rag.knowledge.dto.auth;

public record CurrentUserResponse(
        Long userId,
        String username,
        String role
) {
}
