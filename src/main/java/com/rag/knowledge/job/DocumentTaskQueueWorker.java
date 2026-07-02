package com.rag.knowledge.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rag.knowledge.domain.entity.DocumentTaskQueue;
import com.rag.knowledge.repository.DocumentTaskQueueMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentTaskQueueWorker {

    private static final int BATCH_SIZE = 2;
    private static final int MAX_RETRY_COUNT = 3;

    private final DocumentTaskQueueMapper taskQueueMapper;
    private final DocumentParseTask documentParseTask;

    public DocumentTaskQueueWorker(DocumentTaskQueueMapper taskQueueMapper, DocumentParseTask documentParseTask) {
        this.taskQueueMapper = taskQueueMapper;
        this.documentParseTask = documentParseTask;
    }

    @Scheduled(fixedDelay = 2000L, initialDelay = 1500L)
    public void consume() {
        List<DocumentTaskQueue> tasks = taskQueueMapper.selectList(new LambdaQueryWrapper<DocumentTaskQueue>()
                .eq(DocumentTaskQueue::getTaskType, "DOCUMENT_PARSE")
                .eq(DocumentTaskQueue::getStatus, "PENDING")
                .le(DocumentTaskQueue::getAvailableAt, LocalDateTime.now())
                .orderByAsc(DocumentTaskQueue::getCreatedAt)
                .last("LIMIT " + BATCH_SIZE));
        for (DocumentTaskQueue task : tasks) {
            if (claim(task)) {
                runTask(task);
            }
        }
    }

    private boolean claim(DocumentTaskQueue task) {
        LocalDateTime now = LocalDateTime.now();
        return taskQueueMapper.update(new LambdaUpdateWrapper<DocumentTaskQueue>()
                .eq(DocumentTaskQueue::getId, task.getId())
                .eq(DocumentTaskQueue::getStatus, "PENDING")
                .set(DocumentTaskQueue::getStatus, "RUNNING")
                .set(DocumentTaskQueue::getStartedAt, now)
                .set(DocumentTaskQueue::getUpdatedAt, now)) > 0;
    }

    private void runTask(DocumentTaskQueue task) {
        try {
            documentParseTask.parseQueued(task.getDocumentId());
            markFinished(task, "DONE", null);
        } catch (Exception exception) {
            int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            if (retryCount + 1 >= MAX_RETRY_COUNT) {
                markFinished(task, "FAILED", exception.getMessage());
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            taskQueueMapper.update(new LambdaUpdateWrapper<DocumentTaskQueue>()
                    .eq(DocumentTaskQueue::getId, task.getId())
                    .set(DocumentTaskQueue::getStatus, "PENDING")
                    .set(DocumentTaskQueue::getRetryCount, retryCount + 1)
                    .set(DocumentTaskQueue::getErrorMsg, limitMessage(exception.getMessage()))
                    .set(DocumentTaskQueue::getAvailableAt, now.plusSeconds(5L * (retryCount + 1)))
                    .set(DocumentTaskQueue::getUpdatedAt, now));
        }
    }

    private void markFinished(DocumentTaskQueue task, String status, String errorMsg) {
        LocalDateTime now = LocalDateTime.now();
        taskQueueMapper.update(new LambdaUpdateWrapper<DocumentTaskQueue>()
                .eq(DocumentTaskQueue::getId, task.getId())
                .set(DocumentTaskQueue::getStatus, status)
                .set(DocumentTaskQueue::getErrorMsg, limitMessage(errorMsg))
                .set(DocumentTaskQueue::getFinishedAt, now)
                .set(DocumentTaskQueue::getUpdatedAt, now));
    }

    private String limitMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
