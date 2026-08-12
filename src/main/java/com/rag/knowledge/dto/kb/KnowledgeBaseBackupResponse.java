package com.rag.knowledge.dto.kb;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeBaseBackupResponse(
        String format,
        Integer version,
        LocalDateTime exportedAt,
        KnowledgeBaseItem knowledgeBase,
        List<DocumentItem> documents,
        List<QaRecordItem> qaRecords
) {

    public record KnowledgeBaseItem(
            String name,
            String description,
            String visibility,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer minBreakSize,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record DocumentItem(
            Long originalId,
            String fileName,
            String fileType,
            Long fileSize,
            String status,
            String errorMsg,
            Integer retryCount,
            Integer chunkCount,
            Long parseDurationMs,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<ChunkItem> chunks
    ) {
    }

    public record ChunkItem(
            Integer chunkNo,
            String content,
            Integer charCount,
            String embeddingModel,
            String embeddingJson,
            LocalDateTime createdAt
    ) {
    }

    public record QaRecordItem(
            String question,
            String answer,
            Integer hitCount,
            String citationsJson,
            String answerStyle,
            String answerSource,
            String modelName,
            Long latencyMs,
            Boolean fallback,
            Integer feedbackScore,
            String feedbackNote,
            LocalDateTime feedbackAt,
            LocalDateTime createdAt
    ) {
    }
}
