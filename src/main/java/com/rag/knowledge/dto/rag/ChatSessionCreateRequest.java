package com.rag.knowledge.dto.rag;

import jakarta.validation.constraints.NotNull;

public record ChatSessionCreateRequest(
        @NotNull(message = "知识库不能为空")
        Long kbId
) {
}
