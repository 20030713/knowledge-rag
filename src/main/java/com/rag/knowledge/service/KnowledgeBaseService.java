package com.rag.knowledge.service;

import com.rag.knowledge.dto.kb.KnowledgeBaseCreateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseUpdateRequest;
import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request);

    List<KnowledgeBaseResponse> listMine();

    KnowledgeBaseResponse getMine(Long id);

    KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request);

    void delete(Long id);
}
