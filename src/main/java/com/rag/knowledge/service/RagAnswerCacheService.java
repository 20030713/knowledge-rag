package com.rag.knowledge.service;

import com.rag.knowledge.dto.rag.RagAskResponse;
import java.util.Optional;

public interface RagAnswerCacheService {

    Optional<RagAskResponse> get(Long userId, Long kbId, String question);

    void put(Long userId, Long kbId, String question, RagAskResponse response);

    void evictKnowledgeBase(Long userId, Long kbId);

    void evictKnowledgeBase(Long kbId);
}
