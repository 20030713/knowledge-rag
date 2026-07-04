package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.config.UploadProperties;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.enums.DocumentStatus;
import com.rag.knowledge.dto.doc.DocumentBatchResponse;
import com.rag.knowledge.dto.doc.DocumentChunkResponse;
import com.rag.knowledge.dto.doc.DocumentIndexStatusResponse;
import com.rag.knowledge.dto.doc.DocumentParseResponse;
import com.rag.knowledge.dto.doc.DocumentQualityReportResponse;
import com.rag.knowledge.dto.doc.DocumentResponse;
import com.rag.knowledge.dto.doc.DocumentSearchResultResponse;
import com.rag.knowledge.dto.doc.DocumentStatusResponse;
import com.rag.knowledge.dto.doc.KnowledgeBaseIndexStatusResponse;
import com.rag.knowledge.dto.doc.VectorSyncResponse;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.job.DocumentParseTask;
import com.rag.knowledge.repository.DocumentChunkMapper;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.DistributedLockService;
import com.rag.knowledge.service.DocumentParseProgressService;
import com.rag.knowledge.service.DocumentService;
import com.rag.knowledge.service.DocumentTaskQueueService;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.RagAnswerCacheService;
import com.rag.knowledge.vector.TextEmbeddingService;
import com.rag.knowledge.vector.VectorStoreService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "doc", "docx", "md", "txt");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_PARSE_RETRY_COUNT = 3;
    private static final Duration PARSE_LOCK_TTL = Duration.ofMinutes(10);
    private static final int SEARCH_SNIPPET_RADIUS = 90;
    private static final int DUPLICATE_CHUNK_MIN_LENGTH = 50;
    private static final int OVERSIZED_CHUNK_THRESHOLD = 2000;

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final UploadProperties uploadProperties;
    private final RagAnswerCacheService ragAnswerCacheService;
    private final TextEmbeddingService textEmbeddingService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeBasePermissionService permissionService;
    private final DistributedLockService distributedLockService;
    private final DocumentParseTask documentParseTask;
    private final ObjectMapper objectMapper;
    private final DocumentParseProgressService parseProgressService;
    private final DocumentTaskQueueService taskQueueService;

    public DocumentServiceImpl(
            DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            UploadProperties uploadProperties,
            RagAnswerCacheService ragAnswerCacheService,
            TextEmbeddingService textEmbeddingService,
            VectorStoreService vectorStoreService,
            KnowledgeBasePermissionService permissionService,
            DistributedLockService distributedLockService,
            DocumentParseTask documentParseTask,
            ObjectMapper objectMapper,
            DocumentParseProgressService parseProgressService,
            DocumentTaskQueueService taskQueueService
    ) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.uploadProperties = uploadProperties;
        this.ragAnswerCacheService = ragAnswerCacheService;
        this.textEmbeddingService = textEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.permissionService = permissionService;
        this.distributedLockService = distributedLockService;
        this.documentParseTask = documentParseTask;
        this.objectMapper = objectMapper;
        this.parseProgressService = parseProgressService;
        this.taskQueueService = taskQueueService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentResponse upload(Long kbId, MultipartFile file) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireEdit(kbId);
        validateFile(file);

        String originalName = cleanFileName(file.getOriginalFilename());
        String fileType = extensionOf(originalName);
        String relativePath = buildRelativePath(kbId, fileType);
        Path target = Path.of(uploadProperties.getRootPath()).resolve(relativePath);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "\u6587\u4ef6\u4fdd\u5b58\u5931\u8d25");
        }

        LocalDateTime now = LocalDateTime.now();
        Document document = new Document();
        document.setUserId(access.ownerUserId());
        document.setKbId(kbId);
        document.setFileName(originalName);
        document.setFileType(fileType);
        document.setFileUrl(relativePath.replace('\\', '/'));
        document.setFileSize(file.getSize());
        document.setStatus(DocumentStatus.UPLOADED.name());
        document.setRetryCount(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        ragAnswerCacheService.evictKnowledgeBase(kbId);
        return toResponse(document);
    }

    @Override
    public List<DocumentResponse> listMine(Long kbId) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(kbId);
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, access.ownerUserId())
                        .eq(Document::getKbId, kbId)
                        .orderByDesc(Document::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public DocumentStatusResponse getStatus(Long id) {
        Document document = getReadableDocument(id);
        return new DocumentStatusResponse(
                document.getId(),
                document.getStatus(),
                document.getErrorMsg(),
                safeRetryCount(document),
                safeChunkCount(document),
                document.getParseDurationMs(),
                parseProgressService.get(document.getId()).orElse(null)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentParseResponse parse(Long id) {
        Document document = getEditableDocument(id);
        if (DocumentStatus.PARSING.name().equals(document.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u6587\u6863\u6b63\u5728\u89e3\u6790\u4e2d\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
        }
        if (safeRetryCount(document) >= MAX_PARSE_RETRY_COUNT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u6587\u6863\u89e3\u6790\u5931\u8d25\u6b21\u6570\u8fc7\u591a\uff0c\u8bf7\u91cd\u65b0\u4e0a\u4f20\u6587\u6863");
        }
        markDocumentStatus(document, DocumentStatus.PARSING, null);
        parseProgressService.mark(document.getId(), "QUEUED", 5, "Parse task queued", 0, 0);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, document.getId())
                .eq(DocumentChunk::getUserId, document.getUserId()));
        vectorStoreService.deleteDocument(document.getUserId(), document.getId());
        ragAnswerCacheService.evictKnowledgeBase(document.getKbId());
        taskQueueService.enqueueParse(document);
        return new DocumentParseResponse(document.getId(), document.getStatus(), 0);
    }

    @Override
    public DocumentBatchResponse batchParse(List<Long> ids) {
        List<Long> safeIds = distinctIds(ids);
        if (safeIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u89e3\u6790\u7684\u6587\u6863");
        }
        int submitted = 0;
        List<String> messages = new ArrayList<>();
        for (Long id : safeIds) {
            try {
                parse(id);
                submitted++;
            } catch (BusinessException exception) {
                messages.add("闂佸搫鍊稿ú锕傚Υ?" + id + ": " + exception.getMessage());
            }
        }
        return new DocumentBatchResponse(safeIds.size(), submitted, 0, safeIds.size() - submitted, messages);
    }

    @Override
    public DocumentBatchResponse batchDelete(List<Long> ids) {
        List<Long> safeIds = distinctIds(ids);
        if (safeIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u5220\u9664\u7684\u6587\u6863");
        }
        int deleted = 0;
        List<String> messages = new ArrayList<>();
        for (Long id : safeIds) {
            try {
                delete(id);
                deleted++;
            } catch (BusinessException exception) {
                messages.add("闂佸搫鍊稿ú锕傚Υ?" + id + ": " + exception.getMessage());
            }
        }
        return new DocumentBatchResponse(safeIds.size(), 0, deleted, safeIds.size() - deleted, messages);
    }

    @Override
    public DocumentBatchResponse rebuildKnowledgeBase(Long kbId) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireEdit(kbId);
        List<Long> ids = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, access.ownerUserId())
                        .eq(Document::getKbId, kbId)
                        .orderByDesc(Document::getCreatedAt))
                .stream()
                .map(Document::getId)
                .toList();
        return batchParse(ids);
    }

    @Override
    public List<DocumentChunkResponse> listChunks(Long id) {
        Document document = getReadableDocument(id);
        return documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, document.getId())
                        .eq(DocumentChunk::getUserId, document.getUserId())
                        .orderByAsc(DocumentChunk::getChunkNo))
                .stream()
                .map(this::toChunkResponse)
                .toList();
    }

    @Override
    public List<DocumentSearchResultResponse> searchChunks(Long kbId, String keyword, Integer limit) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(kbId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u8bf7\u8f93\u5165\u641c\u7d22\u5173\u952e\u8bcd");
        }
        if (normalizedKeyword.length() > 80) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u641c\u7d22\u5173\u952e\u8bcd\u4e0d\u80fd\u8d85\u8fc780\u4e2a\u5b57\u7b26");
        }
        int safeLimit = Math.max(1, Math.min(limit == null ? 30 : limit, 100));
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, access.ownerUserId())
                .eq(DocumentChunk::getKbId, kbId)
                .like(DocumentChunk::getContent, normalizedKeyword)
                .orderByDesc(DocumentChunk::getCreatedAt)
                .last("LIMIT " + safeLimit));
        if (chunks.isEmpty()) {
            return List.of();
        }

        Set<Long> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, Document> documentsById = documentMapper.selectBatchIds(documentIds)
                .stream()
                .collect(Collectors.toMap(Document::getId, document -> document));

        return chunks.stream()
                .map(chunk -> toSearchResult(chunk, documentsById.get(chunk.getDocumentId()), normalizedKeyword))
                .toList();
    }

    @Override
    public DocumentQualityReportResponse qualityReport(Long kbId) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(kbId);
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, access.ownerUserId())
                .eq(Document::getKbId, kbId)
                .orderByDesc(Document::getCreatedAt));
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, access.ownerUserId())
                .eq(DocumentChunk::getKbId, kbId)
                .orderByAsc(DocumentChunk::getDocumentId)
                .orderByAsc(DocumentChunk::getChunkNo));
        Map<Long, Document> documentsById = documents.stream()
                .collect(Collectors.toMap(Document::getId, document -> document));

        List<DocumentQualityReportResponse.DuplicateDocumentGroup> duplicateDocumentGroups = documents.stream()
                .collect(Collectors.groupingBy(this::documentDuplicateKey))
                .values()
                .stream()
                .filter(group -> group.size() > 1)
                .map(this::toDuplicateDocumentGroup)
                .sorted((left, right) -> right.count().compareTo(left.count()))
                .limit(20)
                .toList();

        List<DocumentQualityReportResponse.DuplicateChunkGroup> duplicateChunkGroups = chunks.stream()
                .filter(chunk -> normalizedChunkContent(chunk).length() >= DUPLICATE_CHUNK_MIN_LENGTH)
                .collect(Collectors.groupingBy(this::normalizedChunkContent))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> toDuplicateChunkGroup(entry.getKey(), entry.getValue(), documentsById))
                .sorted((left, right) -> right.count().compareTo(left.count()))
                .limit(20)
                .toList();

        int emptyChunkCount = (int) chunks.stream()
                .filter(chunk -> chunk.getContent() == null || chunk.getContent().isBlank())
                .count();
        int oversizedChunkCount = (int) chunks.stream()
                .filter(chunk -> safeCharCount(chunk) > OVERSIZED_CHUNK_THRESHOLD)
                .count();
        return new DocumentQualityReportResponse(
                kbId,
                documents.size(),
                chunks.size(),
                duplicateDocumentGroups.size(),
                duplicateChunkGroups.size(),
                emptyChunkCount,
                oversizedChunkCount,
                duplicateDocumentGroups,
                duplicateChunkGroups
        );
    }

    @Override
    public DocumentIndexStatusResponse indexStatus(Long id) {
        return buildIndexStatus(getReadableDocument(id));
    }

    @Override
    public KnowledgeBaseIndexStatusResponse knowledgeBaseIndexStatus(Long kbId) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(kbId);
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, access.ownerUserId())
                .eq(Document::getKbId, kbId)
                .orderByDesc(Document::getCreatedAt));
        List<DocumentIndexStatusResponse> statuses = documents.stream()
                .map(this::buildIndexStatus)
                .toList();
        int chunkCount = statuses.stream().mapToInt(DocumentIndexStatusResponse::chunkCount).sum();
        int embeddingCount = statuses.stream().mapToInt(DocumentIndexStatusResponse::embeddingCount).sum();
        int vectorStoreCount = vectorStoreService.countKnowledgeBaseVectors(
                access.ownerUserId(),
                kbId,
                textEmbeddingService.modelNamespace()
        );
        return new KnowledgeBaseIndexStatusResponse(
                kbId,
                documents.size(),
                chunkCount,
                embeddingCount,
                vectorStoreCount,
                vectorBackend(),
                aggregateIndexStatus(statuses, chunkCount, embeddingCount, vectorStoreCount),
                statuses
        );
    }

    @Override
    public VectorSyncResponse syncDocumentVectors(Long id) {
        Document document = getEditableDocument(id);
        List<DocumentChunk> chunks = loadDocumentChunks(document);
        return syncChunks(chunks);
    }

    @Override
    public VectorSyncResponse syncKnowledgeBaseVectors(Long kbId) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireEdit(kbId);
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, access.ownerUserId())
                .eq(DocumentChunk::getKbId, kbId)
                .orderByAsc(DocumentChunk::getDocumentId)
                .orderByAsc(DocumentChunk::getChunkNo));
        return syncChunks(chunks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Document document = getEditableDocument(id);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, document.getId())
                .eq(DocumentChunk::getUserId, document.getUserId()));
        vectorStoreService.deleteDocument(document.getUserId(), document.getId());
        documentMapper.deleteById(document.getId());
        parseProgressService.clear(document.getId());
        ragAnswerCacheService.evictKnowledgeBase(document.getKbId());
        deleteStoredFile(document.getFileUrl());
    }

    private void ensureKnowledgeBaseOwned(Long kbId, Long userId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getUserId, userId)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "\u77e5\u8bc6\u5e93\u4e0d\u5b58\u5728");
        }
    }

    private DocumentIndexStatusResponse buildIndexStatus(Document document) {
        List<DocumentChunk> chunks = loadDocumentChunks(document);
        int chunkCount = chunks.size();
        String currentEmbeddingModel = textEmbeddingService.modelNamespace();
        int embeddingCount = (int) chunks.stream()
                .filter(chunk -> currentEmbeddingModel.equals(chunk.getEmbeddingModel()))
                .filter(chunk -> readEmbedding(chunk).length == textEmbeddingService.dimensions())
                .count();
        int vectorStoreCount = vectorStoreService.countDocumentVectors(
                document.getUserId(),
                document.getId(),
                currentEmbeddingModel
        );
        String embeddingModel = chunks.stream()
                .map(DocumentChunk::getEmbeddingModel)
                .filter(model -> model != null && !model.isBlank())
                .findFirst()
                .orElse("-");
        return new DocumentIndexStatusResponse(
                document.getId(),
                document.getKbId(),
                document.getFileName(),
                document.getStatus(),
                chunkCount,
                embeddingCount,
                vectorStoreCount,
                embeddingModel,
                vectorBackend(),
                documentIndexStatus(document, chunkCount, embeddingCount, vectorStoreCount)
        );
    }

    private List<DocumentChunk> loadDocumentChunks(Document document) {
        return documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, document.getId())
                .eq(DocumentChunk::getUserId, document.getUserId())
                .orderByAsc(DocumentChunk::getChunkNo));
    }

    private String documentIndexStatus(Document document, int chunkCount, int embeddingCount, int vectorStoreCount) {
        if (!DocumentStatus.COMPLETED.name().equals(document.getStatus()) || chunkCount == 0) {
            return "NOT_PARSED";
        }
        if (embeddingCount < chunkCount) {
            return "PARTIAL";
        }
        if (!vectorStoreService.available()) {
            return "FALLBACK";
        }
        if (vectorStoreCount == chunkCount) {
            return "SYNCED";
        }
        if (vectorStoreCount == 0) {
            return "EMBEDDING_ONLY";
        }
        return "PARTIAL";
    }

    private String aggregateIndexStatus(
            List<DocumentIndexStatusResponse> statuses,
            int chunkCount,
            int embeddingCount,
            int vectorStoreCount
    ) {
        if (statuses.isEmpty() || chunkCount == 0) {
            return "NOT_PARSED";
        }
        if (embeddingCount < chunkCount) {
            return "PARTIAL";
        }
        if (!vectorStoreService.available()) {
            return "FALLBACK";
        }
        if (vectorStoreCount == chunkCount) {
            return "SYNCED";
        }
        if (vectorStoreCount == 0) {
            return "EMBEDDING_ONLY";
        }
        return "PARTIAL";
    }

    private VectorSyncResponse syncChunks(List<DocumentChunk> chunks) {
        if (!vectorStoreService.available()) {
            return new VectorSyncResponse(chunks.size(), 0, chunks.size(), vectorBackend(), "FALLBACK");
        }
        int synced = 0;
        int skipped = 0;
        String currentEmbeddingModel = textEmbeddingService.modelNamespace();
        for (DocumentChunk chunk : chunks) {
            double[] embedding = readEmbedding(chunk);
            boolean requiresReembedding = !currentEmbeddingModel.equals(chunk.getEmbeddingModel())
                    || embedding.length != textEmbeddingService.dimensions();
            if (requiresReembedding) {
                embedding = textEmbeddingService.embed(chunk.getContent());
            }
            if (embedding.length != textEmbeddingService.dimensions()) {
                skipped++;
                continue;
            }
            if (requiresReembedding) {
                try {
                    chunk.setEmbeddingJson(objectMapper.writeValueAsString(embedding));
                } catch (JsonProcessingException exception) {
                    skipped++;
                    continue;
                }
                chunk.setVectorId(textEmbeddingService.modelName());
                chunk.setEmbeddingModel(currentEmbeddingModel);
                documentChunkMapper.updateById(chunk);
            }
            vectorStoreService.upsert(chunk, embedding);
            synced++;
        }
        chunks.stream()
                .map(DocumentChunk::getKbId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(ragAnswerCacheService::evictKnowledgeBase);
        return new VectorSyncResponse(chunks.size(), synced, skipped, vectorBackend(), skipped == 0 ? "SYNCED" : "PARTIAL");
    }

    private double[] readEmbedding(DocumentChunk chunk) {
        if (chunk.getEmbeddingJson() == null || chunk.getEmbeddingJson().isBlank()) {
            return new double[0];
        }
        try {
            return objectMapper.readValue(chunk.getEmbeddingJson(), double[].class);
        } catch (JsonProcessingException exception) {
            return new double[0];
        }
    }

    private String vectorBackend() {
        return vectorStoreService.available() ? vectorStoreService.backendName() : "local-fallback";
    }

    private Document getReadableDocument(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "\u6587\u6863\u4e0d\u5b58\u5728");
        }
        permissionService.requireRead(document.getKbId());
        return document;
    }

    private Document getEditableDocument(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "\u6587\u6863\u4e0d\u5b58\u5728");
        }
        permissionService.requireEdit(document.getKbId());
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u8bf7\u9009\u62e9\u8981\u4e0a\u4f20\u7684\u6587\u4ef6");
        }
        if (file.getSize() > uploadProperties.maxSizeBytes()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u6587\u4ef6\u5927\u5c0f\u4e0d\u80fd\u8d85\u8fc7" + uploadProperties.getMaxSizeMb() + "MB");
        }

        String fileName = cleanFileName(file.getOriginalFilename());
        String fileType = extensionOf(fileName);
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u4ec5\u652f\u6301 PDF\u3001Word\u3001Markdown \u548c TXT \u6587\u6863");
        }
    }

    private String cleanFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u6587\u4ef6\u540d\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "\u6587\u4ef6\u540e\u7f00\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String buildRelativePath(Long kbId, String fileType) {
        String day = LocalDate.now().format(DAY_FORMATTER);
        return Path.of(String.valueOf(kbId), day, UUID.randomUUID() + "." + fileType).toString();
    }

    private void markDocumentStatus(Document document, DocumentStatus status, String errorMsg) {
        document.setStatus(status.name());
        document.setErrorMsg(errorMsg);
        if (status == DocumentStatus.PARSING || status == DocumentStatus.UPLOADED) {
            document.setChunkCount(0);
            document.setParseDurationMs(null);
        }
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private void deleteStoredFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        Path path = Path.of(uploadProperties.getRootPath()).resolve(fileUrl).normalize();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 闂備礁鎼崐绋棵洪敐鍛瀻闁靛繒濮伴埀顒佸浮瀹曘劑顢涘☉妯峰亾閻愬樊鐔嗛柟顖涘缁ㄥ潡鎮峰▎娆撴缂佸顦濂稿川椤撗勵唵闂備礁鎲＄换鍌滅矓閻㈢姹查柣鏃傚帶缁犲弶銇勯弮鍥т汗婵ǜ鍔戦幃瑙勬媴鐟欏嫮鍑＄紓鍌氱Т缁夌懓鐣峰鑸靛亹缂備焦顭囪ぐ鎴︽⒑?
        }
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getKbId(),
                document.getFileName(),
                document.getFileType(),
                document.getFileUrl(),
                document.getFileSize(),
                document.getStatus(),
                document.getErrorMsg(),
                safeRetryCount(document),
                safeChunkCount(document),
                document.getParseDurationMs(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private DocumentChunkResponse toChunkResponse(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkNo(),
                chunk.getContent(),
                chunk.getCharCount(),
                chunk.getCreatedAt()
        );
    }

    private DocumentSearchResultResponse toSearchResult(DocumentChunk chunk, Document document, String keyword) {
        return new DocumentSearchResultResponse(
                chunk.getDocumentId(),
                document == null ? "\u672a\u77e5\u6587\u6863" : document.getFileName(),
                chunk.getId(),
                chunk.getChunkNo(),
                buildSnippet(chunk.getContent(), keyword),
                chunk.getContent(),
                chunk.getCharCount(),
                countMatches(chunk.getContent(), keyword),
                chunk.getCreatedAt()
        );
    }

    private String buildSnippet(String content, String keyword) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int index = lowerContent.indexOf(lowerKeyword);
        if (index < 0) {
            return content.length() <= SEARCH_SNIPPET_RADIUS * 2
                    ? content
                    : content.substring(0, SEARCH_SNIPPET_RADIUS * 2) + "...";
        }
        int start = Math.max(0, index - SEARCH_SNIPPET_RADIUS);
        int end = Math.min(content.length(), index + keyword.length() + SEARCH_SNIPPET_RADIUS);
        return (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
    }

    private int countMatches(String content, String keyword) {
        if (content == null || content.isBlank() || keyword.isBlank()) {
            return 0;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int count = 0;
        int cursor = 0;
        int index = lowerContent.indexOf(lowerKeyword, cursor);
        while (index >= 0) {
            count++;
            cursor = index + lowerKeyword.length();
            index = lowerContent.indexOf(lowerKeyword, cursor);
        }
        return count;
    }

    private String documentDuplicateKey(Document document) {
        return (document.getFileName() == null ? "" : document.getFileName().trim().toLowerCase(Locale.ROOT))
                + "::"
                + (document.getFileSize() == null ? 0 : document.getFileSize());
    }

    private DocumentQualityReportResponse.DuplicateDocumentGroup toDuplicateDocumentGroup(List<Document> documents) {
        Document first = documents.get(0);
        List<DocumentQualityReportResponse.DocumentItem> items = documents.stream()
                .limit(10)
                .map(document -> new DocumentQualityReportResponse.DocumentItem(
                        document.getId(),
                        document.getFileName(),
                        document.getStatus()
                ))
                .toList();
        return new DocumentQualityReportResponse.DuplicateDocumentGroup(
                first.getFileName(),
                first.getFileSize(),
                documents.size(),
                items
        );
    }

    private DocumentQualityReportResponse.DuplicateChunkGroup toDuplicateChunkGroup(
            String normalizedContent,
            List<DocumentChunk> chunks,
            Map<Long, Document> documentsById
    ) {
        DocumentChunk first = chunks.get(0);
        List<DocumentQualityReportResponse.ChunkItem> items = chunks.stream()
                .limit(10)
                .map(chunk -> {
                    Document document = documentsById.get(chunk.getDocumentId());
                    return new DocumentQualityReportResponse.ChunkItem(
                            chunk.getId(),
                            chunk.getDocumentId(),
                            document == null ? "\u672a\u77e5\u6587\u6863" : document.getFileName(),
                            chunk.getChunkNo()
                    );
                })
                .toList();
        return new DocumentQualityReportResponse.DuplicateChunkGroup(
                Integer.toHexString(normalizedContent.hashCode()),
                buildQualitySnippet(first.getContent()),
                safeCharCount(first),
                chunks.size(),
                items
        );
    }

    private String normalizedChunkContent(DocumentChunk chunk) {
        if (chunk.getContent() == null) {
            return "";
        }
        return chunk.getContent()
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String buildQualitySnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String compact = content.trim().replaceAll("\\s+", " ");
        return compact.length() <= 160 ? compact : compact.substring(0, 160) + "...";
    }

    private int safeCharCount(DocumentChunk chunk) {
        if (chunk.getCharCount() != null) {
            return chunk.getCharCount();
        }
        return chunk.getContent() == null ? 0 : chunk.getContent().length();
    }

    private int safeRetryCount(Document document) {
        return document.getRetryCount() == null ? 0 : document.getRetryCount();
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(100)
                .toList();
    }

    private int safeChunkCount(Document document) {
        return document.getChunkCount() == null ? 0 : document.getChunkCount();
    }
}
