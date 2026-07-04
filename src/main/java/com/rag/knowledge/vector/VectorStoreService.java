package com.rag.knowledge.vector;

import com.rag.knowledge.domain.entity.DocumentChunk;

public interface VectorStoreService {

    boolean available();

    String backendName();

    void initialize();

    void upsert(DocumentChunk chunk, double[] embedding);

    int countDocumentVectors(Long userId, Long documentId, String embeddingModel);

    int countKnowledgeBaseVectors(Long userId, Long kbId, String embeddingModel);

    void deleteDocument(Long userId, Long documentId);

    void deleteKnowledgeBase(Long userId, Long kbId);
}
