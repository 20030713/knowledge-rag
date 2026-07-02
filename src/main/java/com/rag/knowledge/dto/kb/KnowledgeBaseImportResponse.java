package com.rag.knowledge.dto.kb;

public record KnowledgeBaseImportResponse(
        Long kbId,
        String name,
        Integer documentCount,
        Integer chunkCount,
        Integer qaRecordCount,
        String message
) {
}
