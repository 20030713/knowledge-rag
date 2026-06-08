package com.rag.knowledge.dto.auth;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String role
) {
}
