package com.rag.knowledge.vector;

import com.rag.knowledge.domain.entity.DocumentChunk;

public record VectorSearchResult(
        DocumentChunk chunk,
        double vectorScore,
        double keywordScore,
        double finalScore
) {
}
