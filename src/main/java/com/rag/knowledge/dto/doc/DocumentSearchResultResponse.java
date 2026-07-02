package com.rag.knowledge.dto.doc;

import java.time.LocalDateTime;

public record DocumentSearchResultResponse(
        Long documentId,
        String documentName,
        Long chunkId,
        Integer chunkNo,
        String snippet,
        String content,
        Integer charCount,
        Integer matchCount,
        LocalDateTime createdAt
) {
}
