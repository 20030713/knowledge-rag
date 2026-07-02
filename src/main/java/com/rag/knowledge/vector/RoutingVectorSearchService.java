package com.rag.knowledge.vector;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class RoutingVectorSearchService implements VectorSearchService {

    private final PgVectorSearchService pgVectorSearchService;
    private final LocalVectorSearchService localVectorSearchService;

    public RoutingVectorSearchService(
            PgVectorSearchService pgVectorSearchService,
            LocalVectorSearchService localVectorSearchService
    ) {
        this.pgVectorSearchService = pgVectorSearchService;
        this.localVectorSearchService = localVectorSearchService;
    }

    @Override
    public List<VectorSearchResult> search(
            Long userId,
            Long kbId,
            String question,
            int topK,
            double vectorWeight,
            double keywordWeight
    ) {
        if (pgVectorSearchService.available()) {
            List<VectorSearchResult> results = pgVectorSearchService.search(
                    userId,
                    kbId,
                    question,
                    topK,
                    vectorWeight,
                    keywordWeight
            );
            if (!results.isEmpty()) {
                return results;
            }
        }
        return localVectorSearchService.search(userId, kbId, question, topK, vectorWeight, keywordWeight);
    }
}
