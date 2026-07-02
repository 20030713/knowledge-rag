package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.DocumentTaskQueue;
import com.rag.knowledge.repository.DocumentTaskQueueMapper;
import com.rag.knowledge.service.DocumentTaskQueueService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentTaskQueueServiceImpl implements DocumentTaskQueueService {

    private static final String TASK_TYPE_PARSE = "DOCUMENT_PARSE";

    private final DocumentTaskQueueMapper taskQueueMapper;

    public DocumentTaskQueueServiceImpl(DocumentTaskQueueMapper taskQueueMapper) {
        this.taskQueueMapper = taskQueueMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enqueueParse(Document document) {
        DocumentTaskQueue existing = taskQueueMapper.selectOne(new LambdaQueryWrapper<DocumentTaskQueue>()
                .eq(DocumentTaskQueue::getDocumentId, document.getId())
                .eq(DocumentTaskQueue::getTaskType, TASK_TYPE_PARSE)
                .in(DocumentTaskQueue::getStatus, "PENDING", "RUNNING")
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        DocumentTaskQueue task = new DocumentTaskQueue();
        task.setUserId(document.getUserId());
        task.setKbId(document.getKbId());
        task.setDocumentId(document.getId());
        task.setTaskType(TASK_TYPE_PARSE);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setAvailableAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskQueueMapper.insert(task);
    }
}
