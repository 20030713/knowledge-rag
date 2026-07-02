package com.rag.knowledge.dto.doc;

import java.util.List;

public record KnowledgeBaseIndexStatusResponse(
        Long kbId,
        Integer documentCount,
        Integer chunkCount,
        Integer embeddingCount,
        Integer vectorStoreCount,
        String vectorBackend,
        String status,
        List<DocumentIndexStatusResponse> documents
) {
}
