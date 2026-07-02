package com.rag.knowledge.dto.rag;

import java.util.List;

public record RagDebugChunkResponse(
        Long chunkId,
        Long documentId,
        String documentName,
        Integer chunkNo,
        Integer charCount,
        Double vectorScore,
        Double keywordScore,
        Double finalScore,
        List<String> matchedKeywords,
        String content
) {
}
