package com.rag.knowledge.dto.doc;

import java.time.LocalDateTime;

public record DocumentParseProgressResponse(
        Long documentId,
        String stage,
        Integer percent,
        String message,
        Integer processedChunks,
        Integer totalChunks,
        LocalDateTime updatedAt
) {
}
