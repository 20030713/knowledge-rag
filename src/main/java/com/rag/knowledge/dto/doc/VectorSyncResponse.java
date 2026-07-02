package com.rag.knowledge.dto.doc;

public record VectorSyncResponse(
        Integer chunkCount,
        Integer syncedCount,
        Integer skippedCount,
        String vectorBackend,
        String status
) {
}
