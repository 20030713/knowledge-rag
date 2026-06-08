package com.rag.knowledge.security;

public record LoginUser(
        Long userId,
        String username,
        String role
) {
}
