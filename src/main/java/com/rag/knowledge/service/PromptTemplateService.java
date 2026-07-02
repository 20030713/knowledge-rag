package com.rag.knowledge.service;

import com.rag.knowledge.domain.entity.PromptTemplate;
import com.rag.knowledge.dto.rag.PromptTemplateRequest;
import com.rag.knowledge.dto.rag.PromptTemplateResponse;
import com.rag.knowledge.rag.AnswerStyle;
import java.util.List;
import java.util.Optional;

public interface PromptTemplateService {

    List<PromptTemplateResponse> list(Long kbId);

    PromptTemplateResponse create(Long kbId, PromptTemplateRequest request);

    PromptTemplateResponse update(Long id, PromptTemplateRequest request);

    void delete(Long id);

    Optional<PromptTemplate> activeTemplate(Long kbId, AnswerStyle style);
}
