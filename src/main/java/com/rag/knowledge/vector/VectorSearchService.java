package com.rag.knowledge.vector;

import java.util.List;

public interface VectorSearchService {

    List<VectorSearchResult> search(
            Long userId,
            Long kbId,
            String question,
            int topK,
            double vectorWeight,
            double keywordWeight
    );
}
