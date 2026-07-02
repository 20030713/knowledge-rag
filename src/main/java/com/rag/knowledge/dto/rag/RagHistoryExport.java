package com.rag.knowledge.dto.rag;

public record RagHistoryExport(
        String fileName,
        String contentType,
        String content
) {
}
