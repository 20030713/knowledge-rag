package com.rag.knowledge.dto.doc;

import java.util.List;

public record DocumentQualityReportResponse(
        Long kbId,
        Integer documentCount,
        Integer chunkCount,
        Integer duplicateDocumentGroupCount,
        Integer duplicateChunkGroupCount,
        Integer emptyChunkCount,
        Integer oversizedChunkCount,
        List<DuplicateDocumentGroup> duplicateDocumentGroups,
        List<DuplicateChunkGroup> duplicateChunkGroups
) {
    public record DuplicateDocumentGroup(
            String fileName,
            Long fileSize,
            Integer count,
            List<DocumentItem> documents
    ) {
    }

    public record DuplicateChunkGroup(
            String fingerprint,
            String snippet,
            Integer charCount,
            Integer count,
            List<ChunkItem> chunks
    ) {
    }

    public record DocumentItem(
            Long documentId,
            String fileName,
            String status
    ) {
    }

    public record ChunkItem(
            Long chunkId,
            Long documentId,
            String documentName,
            Integer chunkNo
    ) {
    }
}
