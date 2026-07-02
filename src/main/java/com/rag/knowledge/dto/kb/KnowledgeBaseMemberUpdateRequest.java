package com.rag.knowledge.dto.kb;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeBaseMemberUpdateRequest(
        @NotBlank(message = "role is required")
        String role
) {
}
