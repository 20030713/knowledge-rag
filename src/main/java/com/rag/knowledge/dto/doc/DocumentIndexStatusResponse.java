package com.rag.knowledge.dto.doc;

public record DocumentIndexStatusResponse(
        Long documentId,
        Long kbId,
        String fileName,
        String documentStatus,
        Integer chunkCount,
        Integer embeddingCount,
        Integer vectorStoreCount,
        String embeddingModel,
        String vectorBackend,
        String status
) {
}
