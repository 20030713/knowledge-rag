package com.rag.knowledge.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.config.UploadProperties;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.domain.entity.TaskLog;
import com.rag.knowledge.domain.enums.DocumentStatus;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.rag.TextChunker;
import com.rag.knowledge.repository.DocumentChunkMapper;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.TaskLogMapper;
import com.rag.knowledge.service.DistributedLockService;
import com.rag.knowledge.service.DocumentParseProgressService;
import com.rag.knowledge.service.RagAnswerCacheService;
import com.rag.knowledge.storage.TextDocumentReader;
import com.rag.knowledge.vector.TextEmbeddingService;
import com.rag.knowledge.vector.VectorStoreService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DocumentParseTask {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final UploadProperties uploadProperties;
    private final TextDocumentReader textDocumentReader;
    private final TextChunker textChunker;
    private final RagAnswerCacheService ragAnswerCacheService;
    private final DistributedLockService distributedLockService;
    private final TaskLogMapper taskLogMapper;
    private final TextEmbeddingService textEmbeddingService;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;
    private final DocumentParseProgressService parseProgressService;

    public DocumentParseTask(
            DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            UploadProperties uploadProperties,
            TextDocumentReader textDocumentReader,
            TextChunker textChunker,
            RagAnswerCacheService ragAnswerCacheService,
            DistributedLockService distributedLockService,
            TaskLogMapper taskLogMapper,
            TextEmbeddingService textEmbeddingService,
            VectorStoreService vectorStoreService,
            ObjectMapper objectMapper,
            DocumentParseProgressService parseProgressService
    ) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.uploadProperties = uploadProperties;
        this.textDocumentReader = textDocumentReader;
        this.textChunker = textChunker;
        this.ragAnswerCacheService = ragAnswerCacheService;
        this.distributedLockService = distributedLockService;
        this.taskLogMapper = taskLogMapper;
        this.textEmbeddingService = textEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.objectMapper = objectMapper;
        this.parseProgressService = parseProgressService;
    }

    @Async("documentTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void parseAsync(Long documentId, DistributedLockService.DistributedLock lock) {
        parseWithLock(documentId, lock);
    }

    @Transactional(rollbackFor = Exception.class)
    public void parseQueued(Long documentId) {
        DistributedLockService.LockAttempt lockAttempt = distributedLockService.tryLock(
                "lock:doc:parse:" + documentId,
                java.time.Duration.ofMinutes(10)
        );
        if (lockAttempt.available() && !lockAttempt.locked()) {
            return;
        }
        parseWithLock(documentId, lockAttempt.lock());
    }

    private void parseWithLock(Long documentId, DistributedLockService.DistributedLock lock) {
        long startedAt = System.nanoTime();
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            distributedLockService.unlock(lock);
            return;
        }

        try {
            writeLog(document, "STARTED", "Start parsing document: " + document.getFileName(), null);
            parseProgressService.mark(document.getId(), "READING", 10, "Reading source file", 0, 0);
            Path path = Path.of(uploadProperties.getRootPath()).resolve(document.getFileUrl());
            String text = textDocumentReader.read(path, document.getFileType());
            parseProgressService.mark(document.getId(), "CHUNKING", 25, "Splitting document into chunks", 0, 0);
            List<String> chunks = textChunker.split(text);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Document content is empty and cannot be split");
            }

            parseProgressService.mark(document.getId(), "CLEANING", 35, "Clearing old chunks and vectors", 0, chunks.size());
            documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getDocumentId, document.getId())
                    .eq(DocumentChunk::getUserId, document.getUserId()));
            vectorStoreService.deleteDocument(document.getUserId(), document.getId());

            LocalDateTime now = LocalDateTime.now();
            for (int index = 0; index < chunks.size(); index++) {
                String content = chunks.get(index);
                int processed = index + 1;
                parseProgressService.mark(
                        document.getId(),
                        "EMBEDDING",
                        progressPercent(processed, chunks.size()),
                        "Generating embeddings " + processed + "/" + chunks.size(),
                        processed,
                        chunks.size()
                );
                double[] embedding = textEmbeddingService.embed(content);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setUserId(document.getUserId());
                chunk.setKbId(document.getKbId());
                chunk.setDocumentId(document.getId());
                chunk.setChunkNo(index + 1);
                chunk.setContent(content);
                chunk.setCharCount(content.length());
                chunk.setVectorId(textEmbeddingService.modelName());
                chunk.setEmbeddingModel(textEmbeddingService.modelNamespace());
                chunk.setEmbeddingJson(writeEmbedding(embedding));
                chunk.setCreatedAt(now);
                documentChunkMapper.insert(chunk);
                vectorStoreService.upsert(chunk, embedding);
            }

            long durationMs = elapsedMillis(startedAt);
            markDocumentStatus(document, DocumentStatus.COMPLETED, null, chunks.size(), durationMs);
            parseProgressService.mark(document.getId(), "COMPLETED", 100, "Parse completed", chunks.size(), chunks.size());
            writeLog(document, "SUCCESS", "Parse completed, generated " + chunks.size() + " chunks", durationMs);
            ragAnswerCacheService.evictKnowledgeBase(document.getKbId());
        } catch (BusinessException exception) {
            long durationMs = elapsedMillis(startedAt);
            markDocumentStatus(document, DocumentStatus.FAILED, exception.getMessage(), 0, durationMs);
            parseProgressService.mark(document.getId(), "FAILED", 100, exception.getMessage(), 0, 0);
            writeLog(document, "FAILED", exception.getMessage(), durationMs);
        } catch (Exception exception) {
            long durationMs = elapsedMillis(startedAt);
            markDocumentStatus(document, DocumentStatus.FAILED, "Document parse failed", 0, durationMs);
            parseProgressService.mark(document.getId(), "FAILED", 100, "Document parse failed", 0, 0);
            writeLog(document, "FAILED", "Document parse failed: " + exception.getClass().getSimpleName(), durationMs);
        } finally {
            distributedLockService.unlock(lock);
        }
    }

    private int progressPercent(int processedChunks, int totalChunks) {
        if (totalChunks <= 0) {
            return 45;
        }
        return Math.min(95, 40 + (int) Math.round((processedChunks * 55.0) / totalChunks));
    }

    private void markDocumentStatus(Document document, DocumentStatus status, String errorMsg, Integer chunkCount, Long parseDurationMs) {
        document.setStatus(status.name());
        document.setErrorMsg(errorMsg);
        document.setChunkCount(chunkCount);
        document.setParseDurationMs(parseDurationMs);
        if (status == DocumentStatus.COMPLETED) {
            document.setRetryCount(0);
        } else if (status == DocumentStatus.FAILED) {
            document.setRetryCount(safeRetryCount(document) + 1);
        }
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private int safeRetryCount(Document document) {
        return document.getRetryCount() == null ? 0 : document.getRetryCount();
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private void writeLog(Document document, String status, String message, Long durationMs) {
        TaskLog taskLog = new TaskLog();
        taskLog.setUserId(document.getUserId());
        taskLog.setKbId(document.getKbId());
        taskLog.setDocumentId(document.getId());
        taskLog.setTaskType("DOCUMENT_PARSE");
        taskLog.setStatus(status);
        taskLog.setMessage(limitMessage(message));
        taskLog.setDurationMs(durationMs);
        taskLog.setCreatedAt(LocalDateTime.now());
        taskLogMapper.insert(taskLog);
    }

    private String limitMessage(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String writeEmbedding(double[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Embedding serialization failed");
        }
    }
}
