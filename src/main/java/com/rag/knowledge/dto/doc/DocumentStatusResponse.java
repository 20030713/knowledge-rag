package com.rag.knowledge.dto.doc;

public record DocumentStatusResponse(
        Long id,
        String status,
        String errorMsg
) {
}
