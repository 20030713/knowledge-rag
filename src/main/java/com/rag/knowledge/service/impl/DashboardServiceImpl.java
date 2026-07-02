package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.ai.ChatModelService;
import com.rag.knowledge.config.ChatModelProperties;
import com.rag.knowledge.config.EmbeddingModelProperties;
import com.rag.knowledge.config.PgVectorProperties;
import com.rag.knowledge.config.RagCacheProperties;
import com.rag.knowledge.config.RateLimitProperties;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.entity.QaRecord;
import com.rag.knowledge.domain.entity.TaskLog;
import com.rag.knowledge.domain.entity.DocumentTaskQueue;
import com.rag.knowledge.domain.enums.DocumentStatus;
import com.rag.knowledge.dto.dashboard.DashboardOverviewResponse;
import com.rag.knowledge.dto.dashboard.DiagnosticItemResponse;
import com.rag.knowledge.dto.dashboard.DocumentTaskResponse;
import com.rag.knowledge.dto.dashboard.RecentQuestionResponse;
import com.rag.knowledge.dto.dashboard.RuntimeConfigItemResponse;
import com.rag.knowledge.dto.dashboard.RuntimeConfigResponse;
import com.rag.knowledge.dto.dashboard.SystemComponentHealthResponse;
import com.rag.knowledge.dto.dashboard.SystemDiagnosticsResponse;
import com.rag.knowledge.dto.dashboard.SystemHealthResponse;
import com.rag.knowledge.dto.dashboard.TaskLogResponse;
import com.rag.knowledge.dto.dashboard.TodayMetricsResponse;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.DocumentChunkMapper;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.repository.QaRecordMapper;
import com.rag.knowledge.repository.TaskLogMapper;
import com.rag.knowledge.repository.DocumentTaskQueueMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.DashboardService;
import com.rag.knowledge.vector.TextEmbeddingService;
import com.rag.knowledge.vector.VectorStoreService;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final QaRecordMapper qaRecordMapper;
    private final TaskLogMapper taskLogMapper;
    private final DocumentTaskQueueMapper documentTaskQueueMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ChatModelProperties chatModelProperties;
    private final EmbeddingModelProperties embeddingModelProperties;
    private final PgVectorProperties pgVectorProperties;
    private final RagCacheProperties ragCacheProperties;
    private final RateLimitProperties rateLimitProperties;
    private final VectorStoreService vectorStoreService;
    private final ChatModelService chatModelService;
    private final TextEmbeddingService textEmbeddingService;

    public DashboardServiceImpl(
            KnowledgeBaseMapper knowledgeBaseMapper,
            DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            QaRecordMapper qaRecordMapper,
            TaskLogMapper taskLogMapper,
            DocumentTaskQueueMapper documentTaskQueueMapper,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ChatModelProperties chatModelProperties,
            EmbeddingModelProperties embeddingModelProperties,
            PgVectorProperties pgVectorProperties,
            RagCacheProperties ragCacheProperties,
            RateLimitProperties rateLimitProperties,
            VectorStoreService vectorStoreService,
            ChatModelService chatModelService,
            TextEmbeddingService textEmbeddingService
    ) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.qaRecordMapper = qaRecordMapper;
        this.taskLogMapper = taskLogMapper;
        this.documentTaskQueueMapper = documentTaskQueueMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.chatModelProperties = chatModelProperties;
        this.embeddingModelProperties = embeddingModelProperties;
        this.pgVectorProperties = pgVectorProperties;
        this.ragCacheProperties = ragCacheProperties;
        this.rateLimitProperties = rateLimitProperties;
        this.vectorStoreService = vectorStoreService;
        this.chatModelService = chatModelService;
        this.textEmbeddingService = textEmbeddingService;
    }

    @Override
    public DashboardOverviewResponse overview() {
        LoginUser loginUser = UserContext.getRequired();
        Long userId = loginUser.userId();

        Long knowledgeBaseCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId));
        Long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId));
        Long chunkCount = documentChunkMapper.selectCount(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, userId));
        Long qaCount = countQaRecords(userId);

        Long completedDocumentCount = countDocumentsByStatus(userId, DocumentStatus.COMPLETED);
        Long failedDocumentCount = countDocumentsByStatus(userId, DocumentStatus.FAILED);
        Long uploadedDocumentCount = countDocumentsByStatus(userId, DocumentStatus.UPLOADED);
        Long parsingDocumentCount = countDocumentsByStatus(userId, DocumentStatus.PARSING);
        Long feedbackCount = countFeedback(userId, null);
        Long helpfulFeedbackCount = countFeedback(userId, 1);
        Long unhelpfulFeedbackCount = countFeedback(userId, -1);

        List<RecentQuestionResponse> recentQuestions = listRecentQuestions(userId);
        List<DocumentTaskResponse> recentTasks = listRecentTasks(userId);

        return new DashboardOverviewResponse(
                knowledgeBaseCount,
                documentCount,
                chunkCount,
                qaCount,
                completedDocumentCount,
                failedDocumentCount,
                uploadedDocumentCount,
                parsingDocumentCount,
                feedbackCount,
                helpfulFeedbackCount,
                unhelpfulFeedbackCount,
                recentQuestions,
                recentTasks
        );
    }

    @Override
    public SystemHealthResponse health() {
        List<SystemComponentHealthResponse> components = new ArrayList<>();
        components.add(mysqlHealth());
        components.add(redisHealth());
        components.add(chatModelHealth());
        components.add(embeddingModelHealth());
        components.add(vectorStoreHealth());
        components.add(new SystemComponentHealthResponse(
                "RAG_CACHE",
                ragCacheProperties.isEnabled() ? "UP" : "DISABLED",
                ragCacheProperties.isEnabled() ? "TTL " + ragCacheProperties.getTtlMinutes() + " minutes" : "RAG answer cache disabled",
                null
        ));
        components.add(new SystemComponentHealthResponse(
                "RATE_LIMIT",
                rateLimitProperties.isEnabled() ? "UP" : "DISABLED",
                rateLimitProperties.isEnabled()
                        ? "RAG ask " + rateLimitProperties.getRagAsk().limit() + "/" + rateLimitProperties.getRagAsk().windowSeconds() + "s"
                        : "API rate limit disabled",
                null
        ));
        String status = components.stream().anyMatch(component -> "DOWN".equals(component.status())) ? "DEGRADED" : "UP";
        return new SystemHealthResponse(status, LocalDateTime.now(), components);
    }

    @Override
    public TodayMetricsResponse todayMetrics() {
        LoginUser loginUser = UserContext.getRequired();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<QaRecord> records = qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                .eq(QaRecord::getUserId, loginUser.userId())
                .ge(QaRecord::getCreatedAt, todayStart)
                .orderByDesc(QaRecord::getCreatedAt)
                .last("LIMIT 2000"));
        int qaCount = records.size();
        int modelAnswerCount = count(records, record -> "MODEL".equals(record.getAnswerSource()));
        int fallbackCount = count(records, record -> Boolean.TRUE.equals(record.getFallback()));
        int highLatencyCount = count(records, record -> record.getLatencyMs() != null && record.getLatencyMs() >= 5_000);
        long averageLatency = Math.round(records.stream()
                .filter(record -> record.getLatencyMs() != null && record.getLatencyMs() > 0)
                .mapToLong(QaRecord::getLatencyMs)
                .average()
                .orElse(0));
        int modelAttemptCount = modelAnswerCount + fallbackCount;
        int modelSuccessRate = modelAttemptCount <= 0 ? 100 : Math.round((modelAnswerCount * 100.0f) / modelAttemptCount);
        return new TodayMetricsResponse(
                qaCount,
                modelAnswerCount,
                Math.max(0, qaCount - modelAnswerCount),
                fallbackCount,
                highLatencyCount,
                averageLatency,
                modelSuccessRate,
                ragCacheProperties.isEnabled(),
                ragCacheProperties.getTtlMinutes(),
                rateLimitProperties.isEnabled(),
                rateLimitProperties.getRagAsk().limit(),
                rateLimitProperties.getRagAsk().windowSeconds()
        );
    }

    @Override
    public RuntimeConfigResponse runtimeConfig() {
        List<RuntimeConfigItemResponse> items = List.of(
                new RuntimeConfigItemResponse(
                        "CHAT_MODEL",
                        "大模型",
                        chatModelProperties.isEnabled()
                                ? (chatModelProperties.available() ? "UP" : "DOWN")
                                : "DISABLED",
                        chatModelProperties.isEnabled()
                                ? (chatModelProperties.available() ? "API Key 已配置" : "已启用但缺少 API Key")
                                : "未启用，问答会使用本地拼接回答",
                        List.of(
                                "模型：" + emptyToDash(chatModelProperties.getModel()),
                                "地址：" + chatModelProperties.safeBaseUrl() + chatModelProperties.safeEndpointPath(),
                                "超时：" + chatModelProperties.safeTimeoutSeconds() + " 秒",
                                "最大上下文：" + chatModelProperties.safeMaxContextChars() + " 字符",
                                "思考模式：" + (chatModelProperties.isThinkingEnabled() ? "开启" : "关闭")
                        )
                ),
                new RuntimeConfigItemResponse(
                        "EMBEDDING_MODEL",
                        "向量模型",
                        embeddingModelProperties.isEnabled()
                                ? (embeddingModelProperties.available() ? "UP" : "DOWN")
                                : "DISABLED",
                        embeddingModelProperties.isEnabled()
                                ? (embeddingModelProperties.available() ? "API Key 已配置" : "已启用但缺少 API Key")
                                : "未启用，解析时使用本地 Hash 向量",
                        List.of(
                                "模型：" + emptyToDash(embeddingModelProperties.getModel()),
                                "地址：" + embeddingModelProperties.safeBaseUrl() + embeddingModelProperties.safeEndpointPath(),
                                "维度：" + embeddingModelProperties.safeDimensions(),
                                "超时：" + embeddingModelProperties.safeTimeoutSeconds() + " 秒"
                        )
                ),
                new RuntimeConfigItemResponse(
                        "VECTOR_STORE",
                        "向量库",
                        pgVectorProperties.isEnabled()
                                ? (vectorStoreService.available() ? "UP" : "DOWN")
                                : "DISABLED",
                        pgVectorProperties.isEnabled()
                                ? (vectorStoreService.available() ? "pgvector 可用" : "pgvector 不可用，检索会降级")
                                : "未启用，使用 MySQL 本地向量扫描",
                        List.of(
                                "后端：" + vectorStoreService.backendName(),
                                "表名：" + pgVectorProperties.safeTableName(),
                                "维度：" + pgVectorProperties.safeDimensions(),
                                "候选倍数：" + pgVectorProperties.safeCandidateMultiplier(),
                                "自动建表：" + (pgVectorProperties.isInitializeSchema() ? "开启" : "关闭")
                        )
                ),
                new RuntimeConfigItemResponse(
                        "RAG_CACHE",
                        "问答缓存",
                        ragCacheProperties.isEnabled() ? "UP" : "DISABLED",
                        ragCacheProperties.isEnabled()
                                ? "Redis 缓存开启，TTL " + ragCacheProperties.getTtlMinutes() + " 分钟"
                                : "缓存关闭，每次问答都会重新检索",
                        List.of(
                                "缓存介质：Redis",
                                "TTL：" + ragCacheProperties.getTtlMinutes() + " 分钟",
                                "Key 前缀：rag:answer:*"
                        )
                ),
                new RuntimeConfigItemResponse(
                        "RATE_LIMIT",
                        "限流",
                        rateLimitProperties.isEnabled() ? "UP" : "DISABLED",
                        rateLimitProperties.isEnabled()
                                ? "登录和问答接口限流开启"
                                : "限流关闭",
                        List.of(
                                "登录：" + rateLimitProperties.getLogin().limit() + "/" + rateLimitProperties.getLogin().windowSeconds() + " 秒",
                                "问答：" + rateLimitProperties.getRagAsk().limit() + "/" + rateLimitProperties.getRagAsk().windowSeconds() + " 秒"
                        )
                ),
                new RuntimeConfigItemResponse(
                        "DOCUMENT_INDEX",
                        "文档索引",
                        "UP",
                        "文档解析会生成切片、embedding，并按配置同步到向量库",
                        List.of(
                                "切片来源：上传文档本地存储",
                                "索引来源：document_chunk.embedding_json",
                                "向量库同步：可在文档页手动重建或同步"
                        )
                )
        );
        return new RuntimeConfigResponse(LocalDateTime.now(), items);
    }

    @Override
    public SystemDiagnosticsResponse diagnostics() {
        List<DiagnosticItemResponse> items = List.of(
                diagnoseChatModel(),
                diagnoseEmbeddingModel(),
                diagnoseVectorStore(),
                diagnoseRedis()
        );
        String status = items.stream().anyMatch(item -> "DOWN".equals(item.status())) ? "DEGRADED" : "UP";
        return new SystemDiagnosticsResponse(status, LocalDateTime.now(), items);
    }

    @Override
    public List<DocumentTaskResponse> listDocumentTasks(String status, Integer limit) {
        LoginUser loginUser = UserContext.getRequired();
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, loginUser.userId())
                .orderByDesc(Document::getUpdatedAt)
                .last("LIMIT " + safeLimit);
        DocumentStatus parsedStatus = parseStatus(status);
        if (parsedStatus != null && isDocumentStatusFilter(status)) {
            wrapper.eq(Document::getStatus, parsedStatus.name());
        }
        return documentMapper.selectList(wrapper)
                .stream()
                .map(this::toDocumentTask)
                .filter(task -> matchesTaskStatus(task, status))
                .toList();
    }

    @Override
    public List<TaskLogResponse> listTaskLogs(Long documentId) {
        LoginUser loginUser = UserContext.getRequired();
        Document document = documentMapper.selectOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getId, documentId)
                .eq(Document::getUserId, loginUser.userId())
                .last("LIMIT 1"));
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Document not found");
        }

        return taskLogMapper.selectList(new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getUserId, loginUser.userId())
                        .eq(TaskLog::getDocumentId, documentId)
                        .orderByDesc(TaskLog::getCreatedAt)
                        .last("LIMIT 30"))
                .stream()
                .map(this::toTaskLog)
                .toList();
    }

    private Long countDocumentsByStatus(Long userId, DocumentStatus status) {
        return documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .eq(Document::getStatus, status.name()));
    }

    private Long countQaRecords(Long userId) {
        try {
            return qaRecordMapper.selectCount(new LambdaQueryWrapper<QaRecord>()
                    .eq(QaRecord::getUserId, userId));
        } catch (RuntimeException exception) {
            log.warn("Failed to count qa records for dashboard", exception);
            return 0L;
        }
    }

    private Long countFeedback(Long userId, Integer score) {
        try {
            LambdaQueryWrapper<QaRecord> wrapper = new LambdaQueryWrapper<QaRecord>()
                    .eq(QaRecord::getUserId, userId)
                    .isNotNull(QaRecord::getFeedbackScore);
            if (score != null) {
                wrapper.eq(QaRecord::getFeedbackScore, score);
            }
            return qaRecordMapper.selectCount(wrapper);
        } catch (RuntimeException exception) {
            log.warn("Failed to count feedback for dashboard", exception);
            return 0L;
        }
    }

    private SystemComponentHealthResponse mysqlHealth() {
        long startedAt = System.nanoTime();
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return new SystemComponentHealthResponse(
                    "MYSQL",
                    Integer.valueOf(1).equals(value) ? "UP" : "DOWN",
                    Integer.valueOf(1).equals(value) ? "Database connection OK" : "Unexpected database response",
                    elapsedMillis(startedAt)
            );
        } catch (DataAccessException exception) {
            return new SystemComponentHealthResponse("MYSQL", "DOWN", shortMessage(exception), elapsedMillis(startedAt));
        }
    }

    private SystemComponentHealthResponse redisHealth() {
        long startedAt = System.nanoTime();
        try {
            String response = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            boolean ok = "PONG".equalsIgnoreCase(response);
            return new SystemComponentHealthResponse(
                    "REDIS",
                    ok ? "UP" : "DOWN",
                    ok ? "Redis ping OK" : "Unexpected Redis response",
                    elapsedMillis(startedAt)
            );
        } catch (DataAccessException exception) {
            return new SystemComponentHealthResponse("REDIS", "DOWN", shortMessage(exception), elapsedMillis(startedAt));
        }
    }

    private SystemComponentHealthResponse chatModelHealth() {
        if (!chatModelProperties.isEnabled()) {
            return new SystemComponentHealthResponse("CHAT_MODEL", "DISABLED", "Chat model disabled", null);
        }
        if (!chatModelProperties.available()) {
            return new SystemComponentHealthResponse("CHAT_MODEL", "DOWN", "Chat model API key missing", null);
        }
        return new SystemComponentHealthResponse(
                "CHAT_MODEL",
                "UP",
                chatModelProperties.getModel() + " configured at " + chatModelProperties.safeBaseUrl(),
                null
        );
    }

    private SystemComponentHealthResponse embeddingModelHealth() {
        if (!embeddingModelProperties.isEnabled()) {
            return new SystemComponentHealthResponse("EMBEDDING_MODEL", "DISABLED", "Embedding model disabled, using local hashing", null);
        }
        if (!embeddingModelProperties.available()) {
            return new SystemComponentHealthResponse("EMBEDDING_MODEL", "DOWN", "Embedding API key missing", null);
        }
        return new SystemComponentHealthResponse(
                "EMBEDDING_MODEL",
                "UP",
                embeddingModelProperties.getModel() + " configured at " + embeddingModelProperties.safeBaseUrl(),
                null
        );
    }

    private SystemComponentHealthResponse vectorStoreHealth() {
        if (!"pgvector".equals(vectorStoreService.backendName())) {
            return new SystemComponentHealthResponse("VECTOR_STORE", "DISABLED", "Using local MySQL vector scan fallback", null);
        }
        long startedAt = System.nanoTime();
        boolean ok = vectorStoreService.available();
        return new SystemComponentHealthResponse(
                "VECTOR_STORE",
                ok ? "UP" : "DOWN",
                ok ? "pgvector connection OK" : "pgvector unavailable, search falls back to local scan",
                elapsedMillis(startedAt)
        );
    }

    private DiagnosticItemResponse diagnoseChatModel() {
        if (!chatModelProperties.isEnabled()) {
            return new DiagnosticItemResponse(
                    "CHAT_MODEL",
                    "Chat model",
                    "DISABLED",
                    "Chat model disabled",
                    null,
                    List.of("model=" + chatModelProperties.getModel())
            );
        }
        if (!chatModelProperties.available()) {
            return new DiagnosticItemResponse(
                    "CHAT_MODEL",
                    "Chat model",
                    "DOWN",
                    "Chat model API key missing",
                    null,
                    List.of("model=" + chatModelProperties.getModel(), "baseUrl=" + chatModelProperties.safeBaseUrl())
            );
        }
        long startedAt = System.nanoTime();
        try {
            Optional<String> answer = chatModelService.generateAnswer("Reply with OK only.", List.of(), com.rag.knowledge.rag.AnswerStyle.BRIEF);
            long latency = elapsedMillis(startedAt);
            boolean ok = answer.isPresent() && !answer.get().isBlank();
            return new DiagnosticItemResponse(
                    "CHAT_MODEL",
                    "Chat model",
                    ok ? "UP" : "DOWN",
                    ok ? "Chat model call succeeded" : "Chat model returned empty response",
                    latency,
                    List.of(
                            "model=" + chatModelService.modelName(),
                            "baseUrl=" + chatModelProperties.safeBaseUrl(),
                            "endpoint=" + chatModelProperties.safeEndpointPath()
                    )
            );
        } catch (RuntimeException exception) {
            return new DiagnosticItemResponse(
                    "CHAT_MODEL",
                    "Chat model",
                    "DOWN",
                    shortMessage(exception),
                    elapsedMillis(startedAt),
                    List.of("model=" + chatModelProperties.getModel(), "baseUrl=" + chatModelProperties.safeBaseUrl())
            );
        }
    }

    private DiagnosticItemResponse diagnoseEmbeddingModel() {
        if (!embeddingModelProperties.isEnabled()) {
            return new DiagnosticItemResponse(
                    "EMBEDDING_MODEL",
                    "Embedding model",
                    "DISABLED",
                    "Embedding model disabled, using local hash vector",
                    null,
                    List.of("activeDimensions=" + textEmbeddingService.dimensions())
            );
        }
        if (!embeddingModelProperties.available()) {
            return new DiagnosticItemResponse(
                    "EMBEDDING_MODEL",
                    "Embedding model",
                    "DOWN",
                    "Embedding API key missing",
                    null,
                    List.of("model=" + embeddingModelProperties.getModel(), "baseUrl=" + embeddingModelProperties.safeBaseUrl())
            );
        }
        long startedAt = System.nanoTime();
        try {
            double[] vector = textEmbeddingService.embed("knowledge rag diagnostic");
            int actual = vector == null ? 0 : vector.length;
            int expected = embeddingModelProperties.safeDimensions();
            boolean ok = actual == expected;
            return new DiagnosticItemResponse(
                    "EMBEDDING_MODEL",
                    "Embedding model",
                    ok ? "UP" : "DOWN",
                    ok ? "Embedding call succeeded" : "Embedding dimension mismatch, maybe fallback was used",
                    elapsedMillis(startedAt),
                    List.of(
                            "model=" + textEmbeddingService.modelName(),
                            "expectedDimensions=" + expected,
                            "actualDimensions=" + actual,
                            "baseUrl=" + embeddingModelProperties.safeBaseUrl()
                    )
            );
        } catch (RuntimeException exception) {
            return new DiagnosticItemResponse(
                    "EMBEDDING_MODEL",
                    "Embedding model",
                    "DOWN",
                    shortMessage(exception),
                    elapsedMillis(startedAt),
                    List.of("model=" + embeddingModelProperties.getModel(), "baseUrl=" + embeddingModelProperties.safeBaseUrl())
            );
        }
    }

    private DiagnosticItemResponse diagnoseVectorStore() {
        long startedAt = System.nanoTime();
        if (!pgVectorProperties.isEnabled()) {
            return new DiagnosticItemResponse(
                    "VECTOR_STORE",
                    "Vector store",
                    "DISABLED",
                    "pgvector disabled, using local MySQL vector scan",
                    null,
                    List.of("backend=" + vectorStoreService.backendName())
            );
        }
        boolean ok = vectorStoreService.available();
        return new DiagnosticItemResponse(
                "VECTOR_STORE",
                "Vector store",
                ok ? "UP" : "DOWN",
                ok ? "pgvector connection succeeded" : "pgvector unavailable",
                elapsedMillis(startedAt),
                List.of(
                        "backend=" + vectorStoreService.backendName(),
                        "table=" + pgVectorProperties.safeTableName(),
                        "dimensions=" + pgVectorProperties.safeDimensions()
                )
        );
    }

    private DiagnosticItemResponse diagnoseRedis() {
        long startedAt = System.nanoTime();
        String key = "diagnostic:" + UUID.randomUUID();
        try {
            redisTemplate.opsForValue().set(key, "ok", 30, TimeUnit.SECONDS);
            String value = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);
            boolean ok = "ok".equals(value);
            return new DiagnosticItemResponse(
                    "REDIS",
                    "Redis",
                    ok ? "UP" : "DOWN",
                    ok ? "Redis read/write/delete succeeded" : "Redis read returned unexpected value",
                    elapsedMillis(startedAt),
                    List.of("temporaryKey=" + key, "cacheEnabled=" + ragCacheProperties.isEnabled())
            );
        } catch (RuntimeException exception) {
            return new DiagnosticItemResponse(
                    "REDIS",
                    "Redis",
                    "DOWN",
                    shortMessage(exception),
                    elapsedMillis(startedAt),
                    List.of("cacheEnabled=" + ragCacheProperties.isEnabled())
            );
        }
    }

    private int count(List<QaRecord> records, java.util.function.Predicate<QaRecord> predicate) {
        return (int) records.stream().filter(predicate).count();
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private List<RecentQuestionResponse> listRecentQuestions(Long userId) {
        try {
            return qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                            .eq(QaRecord::getUserId, userId)
                            .orderByDesc(QaRecord::getCreatedAt)
                            .last("LIMIT 5"))
                    .stream()
                    .map(this::toRecentQuestion)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Failed to list recent questions for dashboard", exception);
            return List.of();
        }
    }

    private RecentQuestionResponse toRecentQuestion(QaRecord record) {
        return new RecentQuestionResponse(
                record.getId(),
                record.getKbId(),
                record.getQuestion(),
                record.getHitCount(),
                record.getCreatedAt()
        );
    }

    private List<DocumentTaskResponse> listRecentTasks(Long userId) {
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, userId)
                        .in(Document::getStatus, DocumentStatus.PARSING.name(), DocumentStatus.FAILED.name())
                        .orderByDesc(Document::getUpdatedAt)
                        .last("LIMIT 5"))
                .stream()
                .map(this::toDocumentTask)
                .toList();
    }

    private DocumentStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isDocumentStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "UPLOADED".equals(normalized)
                || "PARSING".equals(normalized)
                || "COMPLETED".equals(normalized)
                || "FAILED".equals(normalized);
    }

    private boolean matchesTaskStatus(DocumentTaskResponse task, String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return true;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        String queueStatus = task.queueStatus() == null ? "" : task.queueStatus().toUpperCase(Locale.ROOT);
        String documentStatus = task.status() == null ? "" : task.status().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(normalized)) {
            return "PENDING".equals(queueStatus) || "UPLOADED".equals(documentStatus);
        }
        if ("RUNNING".equals(normalized)) {
            return "RUNNING".equals(queueStatus) || "PARSING".equals(documentStatus);
        }
        if ("DONE".equals(normalized)) {
            return "DONE".equals(queueStatus) || "COMPLETED".equals(documentStatus);
        }
        if ("FAILED".equals(normalized)) {
            return "FAILED".equals(queueStatus) || "FAILED".equals(documentStatus);
        }
        if ("NEEDS_REVIEW".equals(normalized)) {
            int documentRetries = task.retryCount() == null ? 0 : task.retryCount();
            int queueRetries = task.queueRetryCount() == null ? 0 : task.queueRetryCount();
            return "FAILED".equals(documentStatus) && Math.max(documentRetries, queueRetries) >= 3;
        }
        return normalized.equals(documentStatus) || normalized.equals(queueStatus);
    }

    private DocumentTaskResponse toDocumentTask(Document document) {
        DocumentTaskQueue queue = latestQueueTask(document.getId());
        return new DocumentTaskResponse(
                document.getId(),
                document.getKbId(),
                document.getFileName(),
                document.getStatus(),
                document.getErrorMsg(),
                document.getRetryCount() == null ? 0 : document.getRetryCount(),
                document.getChunkCount() == null ? 0 : document.getChunkCount(),
                document.getParseDurationMs(),
                queue == null ? null : queue.getStatus(),
                queue == null || queue.getRetryCount() == null ? 0 : queue.getRetryCount(),
                queue == null ? null : queue.getErrorMsg(),
                queue == null ? null : queue.getAvailableAt(),
                queue == null ? null : queue.getStartedAt(),
                queue == null ? null : queue.getFinishedAt(),
                document.getUpdatedAt()
        );
    }

    private DocumentTaskQueue latestQueueTask(Long documentId) {
        return documentTaskQueueMapper.selectOne(new LambdaQueryWrapper<DocumentTaskQueue>()
                .eq(DocumentTaskQueue::getDocumentId, documentId)
                .eq(DocumentTaskQueue::getTaskType, "DOCUMENT_PARSE")
                .orderByDesc(DocumentTaskQueue::getCreatedAt)
                .last("LIMIT 1"));
    }

    private TaskLogResponse toTaskLog(TaskLog taskLog) {
        return new TaskLogResponse(
                taskLog.getId(),
                taskLog.getDocumentId(),
                taskLog.getTaskType(),
                taskLog.getStatus(),
                taskLog.getMessage(),
                taskLog.getDurationMs(),
                taskLog.getCreatedAt()
        );
    }
}
