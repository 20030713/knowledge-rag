package com.rag.knowledge.dto.rag;

public record RagCitationResponse(
        Long chunkId,
        Long documentId,
        String documentName,
        Integer chunkNo,
        String content,
        Double score,
        Double vectorScore,
        Double keywordScore
) {
}
