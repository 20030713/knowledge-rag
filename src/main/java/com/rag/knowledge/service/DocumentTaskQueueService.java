package com.rag.knowledge.service;

import com.rag.knowledge.domain.entity.Document;

public interface DocumentTaskQueueService {

    void enqueueParse(Document document);
}
