package com.rag.knowledge.dto.kb;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeBaseMemberRequest(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "role is required")
        String role
) {
}
