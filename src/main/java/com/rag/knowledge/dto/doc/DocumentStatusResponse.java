package com.rag.knowledge.dto.doc;

public record DocumentStatusResponse(
        Long documentId,
        String status,
        String errorMsg,
        Integer retryCount,
        Integer chunkCount,
        Long parseDurationMs,
        DocumentParseProgressResponse progress
) {
}
