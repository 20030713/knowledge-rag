package com.rag.knowledge.dto.doc;

import java.time.LocalDateTime;

public record DocumentChunkResponse(
        Long id,
        Long documentId,
        Integer chunkNo,
        String content,
        Integer charCount,
        LocalDateTime createdAt
) {
}
