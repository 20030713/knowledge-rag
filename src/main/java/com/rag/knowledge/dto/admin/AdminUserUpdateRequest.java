package com.rag.knowledge.dto.admin;

public record AdminUserUpdateRequest(
        String role,
        Boolean enabled
) {
}
