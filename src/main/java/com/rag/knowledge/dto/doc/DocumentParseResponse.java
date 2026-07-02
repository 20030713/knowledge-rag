package com.rag.knowledge.dto.doc;

public record DocumentParseResponse(
        Long documentId,
        String status,
        Integer chunkCount
) {
}
