package com.rag.knowledge.dto.doc;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long kbId,
        String fileName,
        String fileType,
        String fileUrl,
        Long fileSize,
        String status,
        String errorMsg,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
