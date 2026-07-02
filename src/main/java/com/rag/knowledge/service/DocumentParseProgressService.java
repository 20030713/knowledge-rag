package com.rag.knowledge.service;

import com.rag.knowledge.dto.doc.DocumentParseProgressResponse;
import java.util.Optional;

public interface DocumentParseProgressService {

    void mark(Long documentId, String stage, int percent, String message, int processedChunks, int totalChunks);

    Optional<DocumentParseProgressResponse> get(Long documentId);

    void clear(Long documentId);
}
