package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.ai.ChatModelService;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.config.VectorSearchProperties;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.domain.entity.ChatMessage;
import com.rag.knowledge.domain.entity.ChatSession;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.entity.PromptTemplate;
import com.rag.knowledge.domain.entity.QaCitation;
import com.rag.knowledge.domain.entity.QaRecord;
import com.rag.knowledge.domain.entity.UserPreference;
import com.rag.knowledge.dto.rag.ChatMessageResponse;
import com.rag.knowledge.dto.rag.ChatSessionCreateRequest;
import com.rag.knowledge.dto.rag.ChatSessionResponse;
import com.rag.knowledge.dto.rag.HotQuestionResponse;
import com.rag.knowledge.dto.rag.QaRecordResponse;
import com.rag.knowledge.dto.rag.QaFeedbackRequest;
import com.rag.knowledge.dto.rag.RagAskRequest;
import com.rag.knowledge.dto.rag.RagAskResponse;
import com.rag.knowledge.dto.rag.RagCitationResponse;
import com.rag.knowledge.dto.rag.RagDebugChunkResponse;
import com.rag.knowledge.dto.rag.RagDebugResponse;
import com.rag.knowledge.dto.rag.RagHistoryExport;
import com.rag.knowledge.dto.rag.RagQualityOverviewResponse;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.rag.AnswerStyle;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.repository.ChatMessageMapper;
import com.rag.knowledge.repository.ChatSessionMapper;
import com.rag.knowledge.repository.QaCitationMapper;
import com.rag.knowledge.repository.QaRecordMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.RagAnswerCacheService;
import com.rag.knowledge.service.RagService;
import com.rag.knowledge.service.RagStreamHandler;
import com.rag.knowledge.service.HotQuestionService;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.PromptTemplateService;
import com.rag.knowledge.service.UserPreferenceService;
import com.rag.knowledge.vector.VectorSearchResult;
import com.rag.knowledge.vector.VectorSearchService;
import com.rag.knowledge.vector.VectorStoreService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagServiceImpl implements RagService {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,，。?!？！：；、（）()《》<>\\[\\]{}\"'`~|/\\\\]+");

    private static final String SOURCE_MODEL = "MODEL";
    private static final String SOURCE_LOCAL_FALLBACK = "LOCAL_FALLBACK";
    private static final long HIGH_LATENCY_THRESHOLD_MS = 5_000L;
    private static final int MEMORY_MESSAGE_LIMIT = 6;
    private static final String INSUFFICIENT_ANSWER = "当前资料不足以确认。请补充相关文档，或换一种更具体的问法。";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final QaCitationMapper qaCitationMapper;
    private final QaRecordMapper qaRecordMapper;
    private final RagAnswerCacheService ragAnswerCacheService;
    private final VectorSearchService vectorSearchService;
    private final VectorStoreService vectorStoreService;
    private final ChatModelService chatModelService;
    private final HotQuestionService hotQuestionService;
    private final VectorSearchProperties vectorSearchProperties;
    private final KnowledgeBasePermissionService permissionService;
    private final UserPreferenceService userPreferenceService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public RagServiceImpl(
            KnowledgeBaseMapper knowledgeBaseMapper,
            DocumentMapper documentMapper,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            QaCitationMapper qaCitationMapper,
            QaRecordMapper qaRecordMapper,
            RagAnswerCacheService ragAnswerCacheService,
            VectorSearchService vectorSearchService,
            VectorStoreService vectorStoreService,
            ChatModelService chatModelService,
            HotQuestionService hotQuestionService,
            VectorSearchProperties vectorSearchProperties,
            KnowledgeBasePermissionService permissionService,
            UserPreferenceService userPreferenceService,
            PromptTemplateService promptTemplateService,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.qaCitationMapper = qaCitationMapper;
        this.qaRecordMapper = qaRecordMapper;
        this.ragAnswerCacheService = ragAnswerCacheService;
        this.vectorSearchService = vectorSearchService;
        this.vectorStoreService = vectorStoreService;
        this.chatModelService = chatModelService;
        this.hotQuestionService = hotQuestionService;
        this.vectorSearchProperties = vectorSearchProperties;
        this.permissionService = permissionService;
        this.userPreferenceService = userPreferenceService;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagAskResponse ask(RagAskRequest request) {
        long startedAt = System.nanoTime();
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(request.kbId());
        ChatSession session = ensureSession(loginUser.userId(), request.kbId(), request.sessionId(), request.question());
        List<ChatMessage> memory = loadRecentMessages(session.getId());
        RagRuntimeOptions options = resolveOptions(loginUser.userId(), request);
        PromptTemplate activeTemplate = promptTemplateService.activeTemplate(request.kbId(), options.style()).orElse(null);
        String modelQuestion = questionWithMemory(memory, request.question());
        String retrievalQuestion = retrievalQuestion(memory, request.question());
        String cacheQuestion = cacheQuestion(session.getId() + ":" + request.question(), options, activeTemplate);
        if (options.enableCache()) {
            RagAskResponse cached = ragAnswerCacheService.get(loginUser.userId(), request.kbId(), cacheQuestion).orElse(null);
            if (cached != null) {
                RagAskResponse response = withCacheHit(cached, session.getId(), elapsedMillis(startedAt));
                saveRecord(loginUser.userId(), session.getId(), response);
                saveConversationTurn(session, request.question(), response.answer());
                return response;
            }
        }
        List<VectorSearchResult> searchResults = vectorSearchService.search(
                access.ownerUserId(),
                request.kbId(),
                retrievalQuestion,
                candidateLimit(options.topK()),
                options.vectorWeight(),
                options.keywordWeight()
        );
        if (searchResults.isEmpty()) {
            return completeInsufficientAnswer(loginUser.userId(), session, request, options, cacheQuestion, startedAt);
        }

        Set<String> terms = tokenize(request.question());
        searchResults = rerank(searchResults, retrievalQuestion, tokenize(retrievalQuestion), options.topK());
        if (searchResults.isEmpty()) {
            return completeInsufficientAnswer(loginUser.userId(), session, request, options, cacheQuestion, startedAt);
        }
        Map<Long, Document> documentMap = loadDocuments(searchResults);
        List<RagCitationResponse> citations = searchResults.stream()
                .map(item -> toCitation(item, documentMap))
                .toList();
        Optional<String> modelAnswer = options.enableModel()
                ? chatModelService.generateAnswer(modelQuestion, citations, options.style(), systemPrompt(activeTemplate, options.style()))
                : Optional.empty();
        boolean generatedByModel = modelAnswer.isPresent();
        boolean modelAbstained = modelAnswer.map(this::isInsufficientAnswer).orElse(false);
        List<RagCitationResponse> effectiveCitations = modelAbstained ? List.of() : citations;
        RagAskResponse response = new RagAskResponse(
                request.kbId(),
                session.getId(),
                request.question(),
                modelAnswer.orElseGet(() -> buildTemplateAnswer(citations, terms, options.style())),
                effectiveCitations.size(),
                effectiveCitations,
                options.style().name(),
                generatedByModel ? SOURCE_MODEL : SOURCE_LOCAL_FALLBACK,
                generatedByModel ? chatModelService.modelName() : null,
                elapsedMillis(startedAt),
                false,
                options.enableModel() && !generatedByModel && chatModelService.available()
        );
        saveRecord(loginUser.userId(), session.getId(), response);
        saveConversationTurn(session, request.question(), response.answer());
        hotQuestionService.record(request.kbId(), request.question());
        if (options.enableCache()) {
            ragAnswerCacheService.put(loginUser.userId(), request.kbId(), cacheQuestion, response);
        }
        return response;
    }

    @Override
    public void streamAsk(RagAskRequest request, RagStreamHandler handler) {
        long startedAt = System.nanoTime();
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(request.kbId());
        ChatSession session = ensureSession(loginUser.userId(), request.kbId(), request.sessionId(), request.question());
        List<ChatMessage> memory = loadRecentMessages(session.getId());
        RagRuntimeOptions options = resolveOptions(loginUser.userId(), request);
        PromptTemplate activeTemplate = promptTemplateService.activeTemplate(request.kbId(), options.style()).orElse(null);
        String modelQuestion = questionWithMemory(memory, request.question());
        String retrievalQuestion = retrievalQuestion(memory, request.question());
        String cacheQuestion = cacheQuestion(session.getId() + ":" + request.question(), options, activeTemplate);
        if (options.enableCache()) {
            RagAskResponse cached = ragAnswerCacheService.get(loginUser.userId(), request.kbId(), cacheQuestion).orElse(null);
            if (cached != null) {
                RagAskResponse cacheHit = withCacheHit(cached, session.getId(), elapsedMillis(startedAt));
                saveRecord(loginUser.userId(), session.getId(), cacheHit);
                saveConversationTurn(session, request.question(), cacheHit.answer());
                handler.onCitations(cacheHit.citations());
                streamText(cacheHit.answer(), handler);
                handler.onComplete(cacheHit);
                return;
            }
        }
        List<VectorSearchResult> searchResults = vectorSearchService.search(
                access.ownerUserId(),
                request.kbId(),
                retrievalQuestion,
                candidateLimit(options.topK()),
                options.vectorWeight(),
                options.keywordWeight()
        );
        if (searchResults.isEmpty()) {
            streamInsufficientAnswer(loginUser.userId(), session, request, options, cacheQuestion, startedAt, handler);
            return;
        }

        Set<String> terms = tokenize(request.question());
        searchResults = rerank(searchResults, retrievalQuestion, tokenize(retrievalQuestion), options.topK());
        if (searchResults.isEmpty()) {
            streamInsufficientAnswer(loginUser.userId(), session, request, options, cacheQuestion, startedAt, handler);
            return;
        }
        Map<Long, Document> documentMap = loadDocuments(searchResults);
        List<RagCitationResponse> citations = searchResults.stream()
                .map(item -> toCitation(item, documentMap))
                .toList();
        Optional<String> modelAnswer = options.enableModel()
                ? chatModelService.streamAnswer(modelQuestion, citations, options.style(), systemPrompt(activeTemplate, options.style()), handler::onDelta)
                : Optional.empty();
        boolean generatedByModel = modelAnswer.isPresent();
        String answer = modelAnswer
                .orElseGet(() -> {
                    String fallback = buildTemplateAnswer(citations, terms, options.style());
                    streamText(fallback, handler);
                    return fallback;
                });
        boolean modelAbstained = generatedByModel && isInsufficientAnswer(answer);
        List<RagCitationResponse> effectiveCitations = modelAbstained ? List.of() : citations;
        handler.onCitations(effectiveCitations);

        RagAskResponse response = new RagAskResponse(
                request.kbId(),
                session.getId(),
                request.question(),
                answer,
                effectiveCitations.size(),
                effectiveCitations,
                options.style().name(),
                generatedByModel ? SOURCE_MODEL : SOURCE_LOCAL_FALLBACK,
                generatedByModel ? chatModelService.modelName() : null,
                elapsedMillis(startedAt),
                false,
                options.enableModel() && !generatedByModel && chatModelService.available()
        );
        saveRecord(loginUser.userId(), session.getId(), response);
        saveConversationTurn(session, request.question(), response.answer());
        hotQuestionService.record(request.kbId(), request.question());
        if (options.enableCache()) {
            ragAnswerCacheService.put(loginUser.userId(), request.kbId(), cacheQuestion, response);
        }
        handler.onComplete(response);
    }

    private RagAskResponse completeInsufficientAnswer(
            Long userId,
            ChatSession session,
            RagAskRequest request,
            RagRuntimeOptions options,
            String cacheQuestion,
            long startedAt
    ) {
        RagAskResponse response = insufficientResponse(session, request, options, startedAt);
        saveRecord(userId, session.getId(), response);
        saveConversationTurn(session, request.question(), response.answer());
        hotQuestionService.record(request.kbId(), request.question());
        if (options.enableCache()) {
            ragAnswerCacheService.put(userId, request.kbId(), cacheQuestion, response);
        }
        return response;
    }

    private void streamInsufficientAnswer(
            Long userId,
            ChatSession session,
            RagAskRequest request,
            RagRuntimeOptions options,
            String cacheQuestion,
            long startedAt,
            RagStreamHandler handler
    ) {
        RagAskResponse response = completeInsufficientAnswer(
                userId, session, request, options, cacheQuestion, startedAt
        );
        handler.onCitations(List.of());
        streamText(response.answer(), handler);
        handler.onComplete(response);
    }

    private RagAskResponse insufficientResponse(
            ChatSession session,
            RagAskRequest request,
            RagRuntimeOptions options,
            long startedAt
    ) {
        return new RagAskResponse(
                request.kbId(),
                session.getId(),
                request.question(),
                INSUFFICIENT_ANSWER,
                0,
                List.of(),
                options.style().name(),
                SOURCE_LOCAL_FALLBACK,
                null,
                elapsedMillis(startedAt),
                false,
                false
        );
    }

    private boolean isInsufficientAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String compact = answer.replaceAll("\\s+", "");
        return compact.contains("资料不足")
                || compact.contains("无法确认")
                || compact.contains("不能确认")
                || compact.contains("没有足够信息");
    }

    private void streamText(String text, RagStreamHandler handler) {
        int step = 12;
        for (int index = 0; index < text.length(); index += step) {
            handler.onDelta(text.substring(index, Math.min(index + step, text.length())));
        }
    }

    private RagRuntimeOptions resolveOptions(Long userId, RagAskRequest request) {
        UserPreference preference = userPreferenceService.getOrCreate(userId);
        AnswerStyle style = request.answerStyle() == null || request.answerStyle().isBlank()
                ? AnswerStyle.from(preference.getDefaultAnswerStyle())
                : AnswerStyle.from(request.answerStyle());
        int topK = clamp(
                request.topK() == null ? preference.getDefaultTopK() : request.topK(),
                1,
                20,
                vectorSearchProperties.safeTopK()
        );
        double vectorWeight = clampWeight(
                request.vectorWeight() == null ? preference.getVectorWeight() : request.vectorWeight(),
                vectorSearchProperties.getVectorWeight()
        );
        double keywordWeight = clampWeight(
                request.keywordWeight() == null ? preference.getKeywordWeight() : request.keywordWeight(),
                vectorSearchProperties.getKeywordWeight()
        );
        boolean enableModel = request.enableModel() == null
                ? preference.getEnableModel() == null || preference.getEnableModel()
                : request.enableModel();
        boolean enableCache = request.enableCache() == null
                ? preference.getEnableCache() == null || preference.getEnableCache()
                : request.enableCache();
        return new RagRuntimeOptions(style, topK, vectorWeight, keywordWeight, enableModel, enableCache);
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(value, max));
    }

    private double clampWeight(Double value, double fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, Math.min(value, 1));
    }

    private String cacheQuestion(String question, RagRuntimeOptions options, PromptTemplate template) {
        return question + "\n\n__answer_mode__:" + chatModelService.cacheNamespace()
                + "\n__answer_style__:" + options.style().cacheKey()
                + "\n__top_k__:" + options.topK()
                + "\n__vector_weight__:" + options.vectorWeight()
                + "\n__keyword_weight__:" + options.keywordWeight()
                + "\n__model_enabled__:" + options.enableModel()
                + "\n__prompt_template__:" + promptTemplateCacheKey(template);
    }

    private String promptTemplateCacheKey(PromptTemplate template) {
        if (template == null) {
            return "builtin";
        }
        return template.getId() + ":" + template.getUpdatedAt();
    }

    private String systemPrompt(PromptTemplate template, AnswerStyle style) {
        if (template == null || template.getSystemPrompt() == null || template.getSystemPrompt().isBlank()) {
            return null;
        }
        return template.getSystemPrompt()
                .replace("{style}", style == null ? AnswerStyle.STRICT.name() : style.name());
    }

    private record RagRuntimeOptions(
            AnswerStyle style,
            int topK,
            double vectorWeight,
            double keywordWeight,
            boolean enableModel,
            boolean enableCache
    ) {
    }

    @Override
    public RagDebugResponse debug(RagAskRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(request.kbId());
        long startedAt = System.nanoTime();
        RagRuntimeOptions options = resolveOptions(loginUser.userId(), request);
        PromptTemplate activeTemplate = promptTemplateService.activeTemplate(request.kbId(), options.style()).orElse(null);
        String cacheQuestion = cacheQuestion(request.question(), options, activeTemplate);
        boolean cacheHit = options.enableCache()
                && ragAnswerCacheService.get(loginUser.userId(), request.kbId(), cacheQuestion).isPresent();

        List<VectorSearchResult> searchResults = vectorSearchService.search(
                access.ownerUserId(),
                request.kbId(),
                request.question(),
                candidateLimit(options.topK()),
                options.vectorWeight(),
                options.keywordWeight()
        );
        Set<String> terms = tokenize(request.question());
        searchResults = rerank(searchResults, request.question(), terms, options.topK());
        Map<Long, Document> documentMap = loadDocuments(searchResults);
        List<RagDebugChunkResponse> chunks = searchResults.stream()
                .map(result -> toDebugChunk(result, documentMap, terms))
                .toList();
        return new RagDebugResponse(
                request.kbId(),
                request.question(),
                options.topK(),
                roundScore(options.vectorWeight()),
                roundScore(options.keywordWeight()),
                cacheHit,
                (options.enableModel() ? chatModelService.cacheNamespace() : "local-only") + ":" + options.style().cacheKey(),
                vectorStoreService.available() ? vectorStoreService.backendName() : "local-fallback",
                elapsedMillis(startedAt),
                terms.stream().limit(30).toList(),
                chunks
        );
    }

    @Override
    public ChatSessionResponse createSession(ChatSessionCreateRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireRead(request.kbId());
        ChatSession session = createSession(loginUser.userId(), request.kbId(), "新的对话");
        return toSessionResponse(session);
    }

    @Override
    public List<ChatSessionResponse> listSessions(Long kbId) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireRead(kbId);
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, loginUser.userId())
                        .eq(ChatSession::getKbId, kbId)
                        .orderByDesc(ChatSession::getUpdatedAt)
                        .last("LIMIT 50"))
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    public List<ChatMessageResponse> listSessionMessages(Long sessionId) {
        ChatSession session = getReadableSession(sessionId);
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .orderByAsc(ChatMessage::getCreatedAt))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    public void deleteSession(Long sessionId) {
        ChatSession session = getReadableSession(sessionId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId()));
        qaRecordMapper.update(new LambdaUpdateWrapper<QaRecord>()
                .eq(QaRecord::getSessionId, session.getId())
                .set(QaRecord::getSessionId, null));
        chatSessionMapper.deleteById(session.getId());
    }

    @Override
    public List<HotQuestionResponse> listHotQuestions(Long kbId, Integer limit) {
        permissionService.requireRead(kbId);
        return hotQuestionService.list(kbId, limit);
    }

    @Override
    public void clearCache(Long kbId) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireAdmin(kbId);
        ragAnswerCacheService.evictKnowledgeBase(kbId);
    }

    @Override
    public List<QaRecordResponse> listHistory(Long kbId) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireRead(kbId);
        return qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                        .eq(QaRecord::getUserId, loginUser.userId())
                        .eq(QaRecord::getKbId, kbId)
                        .orderByDesc(QaRecord::getCreatedAt)
                        .last("LIMIT 30"))
                .stream()
                .map(this::toRecordResponse)
                .toList();
    }

    @Override
    public RagHistoryExport exportHistory(Long kbId, String format) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireRead(kbId);
        List<QaRecordResponse> records = qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                        .eq(QaRecord::getUserId, loginUser.userId())
                        .eq(QaRecord::getKbId, kbId)
                        .orderByDesc(QaRecord::getCreatedAt)
                        .last("LIMIT 1000"))
                .stream()
                .map(this::toRecordResponse)
                .toList();
        if ("md".equalsIgnoreCase(format) || "markdown".equalsIgnoreCase(format)) {
            return new RagHistoryExport(
                    "rag-history-kb-" + kbId + ".md",
                    "text/markdown; charset=UTF-8",
                    exportMarkdown(kbId, records)
            );
        }
        return new RagHistoryExport(
                "rag-history-kb-" + kbId + ".csv",
                "text/csv; charset=UTF-8",
                exportCsv(records)
        );
    }

    @Override
    public RagQualityOverviewResponse qualityOverview(Long kbId) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireRead(kbId);
        List<QaRecord> records = loadQualityRecords(loginUser.userId(), kbId);
        int totalCount = records.size();
        int helpfulCount = count(records, record -> Integer.valueOf(1).equals(record.getFeedbackScore()));
        int unhelpfulCount = count(records, record -> Integer.valueOf(-1).equals(record.getFeedbackScore()));
        int feedbackCount = helpfulCount + unhelpfulCount;
        int modelAnswerCount = count(records, record -> SOURCE_MODEL.equals(record.getAnswerSource()));
        int fallbackCount = count(records, record -> Boolean.TRUE.equals(record.getFallback()));
        int noCitationCount = count(records, record -> safeHitCount(record) <= 0);
        int highLatencyCount = count(records, this::isHighLatency);
        long averageLatency = Math.round(records.stream()
                .filter(record -> record.getLatencyMs() != null && record.getLatencyMs() > 0)
                .mapToLong(QaRecord::getLatencyMs)
                .average()
                .orElse(0));
        return new RagQualityOverviewResponse(
                kbId,
                totalCount,
                feedbackCount,
                helpfulCount,
                unhelpfulCount,
                Math.max(0, totalCount - feedbackCount),
                modelAnswerCount,
                Math.max(0, totalCount - modelAnswerCount),
                fallbackCount,
                noCitationCount,
                highLatencyCount,
                averageLatency,
                qualityScore(totalCount, helpfulCount, unhelpfulCount, fallbackCount, noCitationCount, highLatencyCount)
        );
    }

    @Override
    public List<QaRecordResponse> listQualityIssues(Long kbId, String type, Integer limit) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireRead(kbId);
        String normalizedType = type == null ? "ALL" : type.trim().toUpperCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        return loadQualityRecords(loginUser.userId(), kbId).stream()
                .filter(record -> matchesQualityIssue(record, normalizedType))
                .limit(safeLimit)
                .map(this::toRecordResponse)
                .toList();
    }

    private List<QaRecord> loadQualityRecords(Long userId, Long kbId) {
        return qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                .eq(QaRecord::getUserId, userId)
                .eq(QaRecord::getKbId, kbId)
                .orderByDesc(QaRecord::getCreatedAt)
                .last("LIMIT 1000"));
    }

    private boolean matchesQualityIssue(QaRecord record, String type) {
        return switch (type) {
            case "UNHELPFUL" -> Integer.valueOf(-1).equals(record.getFeedbackScore());
            case "FALLBACK" -> Boolean.TRUE.equals(record.getFallback());
            case "NO_CITATION" -> safeHitCount(record) <= 0;
            case "HIGH_LATENCY" -> isHighLatency(record);
            case "NO_FEEDBACK" -> record.getFeedbackScore() == null;
            default -> Integer.valueOf(-1).equals(record.getFeedbackScore())
                    || Boolean.TRUE.equals(record.getFallback())
                    || safeHitCount(record) <= 0
                    || isHighLatency(record);
        };
    }

    private boolean isHighLatency(QaRecord record) {
        return record.getLatencyMs() != null && record.getLatencyMs() >= HIGH_LATENCY_THRESHOLD_MS;
    }

    private int safeHitCount(QaRecord record) {
        return record.getHitCount() == null ? 0 : record.getHitCount();
    }

    private int count(List<QaRecord> records, java.util.function.Predicate<QaRecord> predicate) {
        return (int) records.stream().filter(predicate).count();
    }

    private int qualityScore(
            int totalCount,
            int helpfulCount,
            int unhelpfulCount,
            int fallbackCount,
            int noCitationCount,
            int highLatencyCount
    ) {
        if (totalCount <= 0) {
            return 100;
        }
        int score = 100;
        score -= Math.round((unhelpfulCount * 30.0f) / totalCount);
        score -= Math.round((fallbackCount * 20.0f) / totalCount);
        score -= Math.round((noCitationCount * 25.0f) / totalCount);
        score -= Math.round((highLatencyCount * 10.0f) / totalCount);
        score += Math.round((helpfulCount * 5.0f) / totalCount);
        return Math.max(0, Math.min(score, 100));
    }

    @Override
    public QaRecordResponse feedback(Long id, QaFeedbackRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        QaRecord record = qaRecordMapper.selectOne(new LambdaQueryWrapper<QaRecord>()
                .eq(QaRecord::getId, id)
                .eq(QaRecord::getUserId, loginUser.userId())
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问答记录不存在");
        }
        permissionService.requireRead(record.getKbId());
        Integer score = request.feedbackScore();
        if (score == null || score == 0) {
            record.setFeedbackScore(null);
            record.setFeedbackNote(null);
            record.setFeedbackAt(null);
        } else {
            record.setFeedbackScore(score);
            record.setFeedbackNote(normalizeNote(request.feedbackNote()));
            record.setFeedbackAt(LocalDateTime.now());
        }
        qaRecordMapper.update(new LambdaUpdateWrapper<QaRecord>()
                .eq(QaRecord::getId, record.getId())
                .eq(QaRecord::getUserId, loginUser.userId())
                .set(QaRecord::getFeedbackScore, record.getFeedbackScore())
                .set(QaRecord::getFeedbackNote, record.getFeedbackNote())
                .set(QaRecord::getFeedbackAt, record.getFeedbackAt()));
        return toRecordResponse(record);
    }

    @Override
    public void deleteHistory(Long id) {
        LoginUser loginUser = UserContext.getRequired();
        QaRecord record = qaRecordMapper.selectOne(new LambdaQueryWrapper<QaRecord>()
                .eq(QaRecord::getId, id)
                .eq(QaRecord::getUserId, loginUser.userId())
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问答记录不存在");
        }
        qaCitationMapper.delete(new LambdaQueryWrapper<QaCitation>()
                .eq(QaCitation::getQaRecordId, record.getId())
                .eq(QaCitation::getUserId, loginUser.userId()));
        qaRecordMapper.deleteById(record.getId());
    }

    private void ensureKnowledgeBaseOwned(Long kbId, Long userId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getUserId, userId)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
    }

    private Set<String> tokenize(String question) {
        String normalized = question.toLowerCase(Locale.ROOT).trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : SPLIT_PATTERN.split(normalized)) {
            if (term.length() >= 2) {
                terms.add(term);
            }
        }
        String cjkOnly = normalized.chars()
                .filter(this::isCjk)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        for (int index = 0; index < cjkOnly.length() - 1; index++) {
            terms.add(cjkOnly.substring(index, index + 2));
        }
        if (terms.isEmpty() && !normalized.isBlank()) {
            terms.add(normalized);
        }
        return terms;
    }

    private boolean isCjk(int codePoint) {
        return codePoint >= 0x4E00 && codePoint <= 0x9FFF;
    }

    private int candidateLimit(int topK) {
        return Math.max(topK, Math.min(80, topK * vectorSearchProperties.safeCandidateMultiplier()));
    }

    private List<VectorSearchResult> rerank(
            List<VectorSearchResult> results,
            String question,
            Set<String> terms,
            int topK
    ) {
        String normalizedQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT).trim();
        List<VectorSearchResult> scored = results.stream()
                .map(result -> rerankedResult(result, normalizedQuestion, terms))
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore()))
                .toList();
        if (scored.isEmpty() || !hasEnoughEvidence(scored.getFirst(), terms)) {
            return List.of();
        }
        List<VectorSearchResult> selected = new ArrayList<>();
        List<VectorSearchResult> remaining = new ArrayList<>(scored.stream()
                .filter(result -> result.finalScore() >= vectorSearchProperties.safeMinRelevanceScore())
                .toList());
        while (!remaining.isEmpty() && selected.size() < topK) {
            VectorSearchResult next = remaining.stream()
                    .max((left, right) -> Double.compare(
                            diversifiedScore(left, selected),
                            diversifiedScore(right, selected)
                    ))
                    .orElseThrow();
            remaining.remove(next);
            double similarity = maxSimilarity(next, selected);
            if (similarity < 0.92) {
                selected.add(new VectorSearchResult(
                        next.chunk(),
                        next.vectorScore(),
                        next.keywordScore(),
                        Math.max(0, diversifiedScore(next, selected))
                ));
            }
        }
        return selected;
    }

    private boolean hasEnoughEvidence(VectorSearchResult topResult, Set<String> terms) {
        String content = topResult.chunk().getContent() == null ? "" : topResult.chunk().getContent().toLowerCase(Locale.ROOT);
        double keywordCoverage = keywordCoverageScore(content, terms);
        return topResult.vectorScore() >= vectorSearchProperties.safeSemanticConfidenceScore()
                || keywordCoverage >= vectorSearchProperties.safeMinKeywordCoverage();
    }

    private double diversifiedScore(VectorSearchResult candidate, List<VectorSearchResult> selected) {
        return candidate.finalScore()
                - vectorSearchProperties.safeDiversityPenalty() * maxSimilarity(candidate, selected);
    }

    private double maxSimilarity(VectorSearchResult candidate, List<VectorSearchResult> selected) {
        return selected.stream()
                .mapToDouble(item -> chunkSimilarity(candidate.chunk().getContent(), item.chunk().getContent()))
                .max()
                .orElse(0);
    }

    private double chunkSimilarity(String left, String right) {
        Set<String> leftShingles = shingles(left);
        Set<String> rightShingles = shingles(right);
        if (leftShingles.isEmpty() || rightShingles.isEmpty()) {
            return 0;
        }
        long intersection = leftShingles.stream().filter(rightShingles::contains).count();
        long union = leftShingles.size() + rightShingles.size() - intersection;
        return union == 0 ? 0 : intersection * 1.0 / union;
    }

    private Set<String> shingles(String content) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (int index = 0; index < normalized.length() - 2; index++) {
            values.add(normalized.substring(index, index + 3));
        }
        return values;
    }

    private VectorSearchResult rerankedResult(VectorSearchResult result, String normalizedQuestion, Set<String> terms) {
        String content = result.chunk().getContent() == null
                ? ""
                : result.chunk().getContent().toLowerCase(Locale.ROOT);
        double coverageScore = keywordCoverageScore(content, terms);
        double phraseScore = normalizedQuestion.length() >= 4 && content.contains(normalizedQuestion) ? 1.0 : 0.0;
        double lengthScore = chunkLengthScore(result.chunk().getCharCount());
        double rerankScore = result.finalScore() * 0.68
                + coverageScore * 0.22
                + phraseScore * 0.07
                + lengthScore * 0.03;
        return new VectorSearchResult(result.chunk(), result.vectorScore(), result.keywordScore(), rerankScore);
    }

    private double keywordCoverageScore(String content, Set<String> terms) {
        List<String> usefulTerms = terms.stream()
                .filter(term -> term.length() >= 2)
                .limit(20)
                .toList();
        if (usefulTerms.isEmpty()) {
            return 0;
        }
        long matched = usefulTerms.stream().filter(content::contains).count();
        return matched * 1.0 / usefulTerms.size();
    }

    private double chunkLengthScore(Integer charCount) {
        int length = charCount == null ? 0 : charCount;
        if (length <= 0) {
            return 0;
        }
        if (length >= 180 && length <= 1200) {
            return 1.0;
        }
        if (length < 180) {
            return Math.max(0.2, length / 180.0);
        }
        return Math.max(0.2, 1200.0 / length);
    }

    private Map<Long, Document> loadDocuments(List<VectorSearchResult> searchResults) {
        List<Long> documentIds = searchResults.stream()
                .map(item -> item.chunk().getDocumentId())
                .distinct()
                .toList();
        return documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
    }

    private RagCitationResponse toCitation(VectorSearchResult searchResult, Map<Long, Document> documentMap) {
        DocumentChunk chunk = searchResult.chunk();
        Document document = documentMap.get(chunk.getDocumentId());
        String documentName = document == null ? "未知文档" : document.getFileName();
        return new RagCitationResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                documentName,
                chunk.getChunkNo(),
                chunk.getContent(),
                roundScore(searchResult.finalScore()),
                roundScore(searchResult.vectorScore()),
                roundScore(searchResult.keywordScore())
        );
    }

    private RagDebugChunkResponse toDebugChunk(
            VectorSearchResult searchResult,
            Map<Long, Document> documentMap,
            Set<String> terms
    ) {
        DocumentChunk chunk = searchResult.chunk();
        Document document = documentMap.get(chunk.getDocumentId());
        String documentName = document == null ? "未知文档" : document.getFileName();
        return new RagDebugChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                documentName,
                chunk.getChunkNo(),
                chunk.getCharCount(),
                roundScore(searchResult.vectorScore()),
                roundScore(searchResult.keywordScore()),
                roundScore(searchResult.finalScore()),
                matchedKeywords(chunk.getContent(), terms),
                chunk.getContent()
        );
    }

    private List<String> matchedKeywords(String content, Set<String> terms) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        return terms.stream()
                .filter(term -> term.length() >= 2)
                .filter(normalized::contains)
                .limit(16)
                .toList();
    }

    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private String buildTemplateAnswer(List<RagCitationResponse> citations, Set<String> terms, AnswerStyle style) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add("结论");
        if (style == AnswerStyle.BRIEF) {
            paragraphs.add("命中了 " + citations.size() + " 个相关片段，可优先参考下方引用。");
        } else if (style == AnswerStyle.INTERVIEW) {
            paragraphs.add("当前知识库命中了 " + citations.size() + " 个相关片段，可以把它们组织成面试讲解材料。");
        } else {
            paragraphs.add("当前知识库命中了 " + citations.size() + " 个相关片段，可以优先参考下面这些内容。");
        }
        paragraphs.add("要点");
        int limit = style == AnswerStyle.BRIEF ? 2 : Math.min(3, citations.size());
        for (int index = 0; index < Math.min(limit, citations.size()); index++) {
            RagCitationResponse citation = citations.get(index);
            paragraphs.add((index + 1) + ". " + summarize(citation.content(), terms));
        }
        if (style == AnswerStyle.INTERVIEW) {
            paragraphs.add("3. 面试表达时可以先讲业务问题，再讲检索、缓存、降级等工程处理。");
        }
        paragraphs.add("说明");
        paragraphs.add("以上内容来自下方引用片段。当前版本使用本地向量检索 + 关键词混合排序，后续可以替换为真实 Embedding 模型、Milvus 或 pgvector，并接入大模型生成。");
        paragraphs.set(paragraphs.size() - 1, "以上内容来自下方引用片段。当前未启用大模型，或模型调用失败，系统已自动降级为本地模板化回答。");
        return String.join("\n", paragraphs);
    }

    private String summarize(String content, Set<String> terms) {
        String compact = cleanMarkdown(content);
        int bestIndex = terms.stream()
                .mapToInt(compact::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(0);
        int start = Math.max(0, bestIndex - 45);
        int end = Math.min(compact.length(), bestIndex + 155);
        String snippet = compact.substring(start, end).trim();
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < compact.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private String cleanMarkdown(String content) {
        String compact = content
                .replaceAll("#{1,6}\\s*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\|\\s*-+\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (compact.length() <= 220) {
            return compact;
        }
        return compact.substring(0, 220);
    }

    private ChatSession ensureSession(Long userId, Long kbId, Long sessionId, String question) {
        if (sessionId != null) {
            ChatSession session = getReadableSession(sessionId);
            if (!session.getKbId().equals(kbId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "对话不属于当前知识库");
            }
            return session;
        }
        return createSession(userId, kbId, sessionTitle(question));
    }

    private ChatSession createSession(Long userId, Long kbId, String title) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setKbId(kbId);
        session.setTitle(title);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        chatSessionMapper.insert(session);
        return session;
    }

    private ChatSession getReadableSession(Long sessionId) {
        LoginUser loginUser = UserContext.getRequired();
        ChatSession session = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getUserId, loginUser.userId())
                .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对话不存在");
        }
        permissionService.requireRead(session.getKbId());
        return session;
    }

    private List<ChatMessage> loadRecentMessages(Long sessionId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT " + MEMORY_MESSAGE_LIMIT));
        return messages.reversed();
    }

    private String questionWithMemory(List<ChatMessage> memory, String question) {
        if (memory.isEmpty()) {
            return question;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("最近对话上下文：\n");
        for (ChatMessage message : memory) {
            builder.append("USER".equals(message.getRole()) ? "用户：" : "助手：")
                    .append(trimForMemory(message.getContent()))
                    .append("\n");
        }
        builder.append("\n当前问题：").append(question);
        return builder.toString();
    }

    private String retrievalQuestion(List<ChatMessage> memory, String question) {
        if (memory.isEmpty() || !looksLikeFollowUp(question)) {
            return question;
        }
        String previousUserQuestion = memory.reversed().stream()
                .filter(message -> "USER".equals(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse("");
        if (previousUserQuestion.isBlank()) {
            return question;
        }
        return trimForMemory(previousUserQuestion) + "\n" + question;
    }

    private boolean looksLikeFollowUp(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String compact = question.replaceAll("\\s+", "");
        return compact.length() <= 14
                || compact.matches(".*(?:它|这个|该|上述|前面|这些|那|其|此).*");
    }

    private String trimForMemory(String content) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() <= 300 ? compact : compact.substring(0, 300) + "...";
    }

    private void saveConversationTurn(ChatSession session, String question, String answer) {
        LocalDateTime now = LocalDateTime.now();
        saveMessage(session, "USER", question, now);
        saveMessage(session, "ASSISTANT", answer, now.plusNanos(1));
        chatSessionMapper.update(new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getUpdatedAt, LocalDateTime.now())
                .set(ChatSession::getTitle, sessionTitle(session.getTitle(), question)));
    }

    private void saveMessage(ChatSession session, String role, String content, LocalDateTime createdAt) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setUserId(session.getUserId());
        message.setKbId(session.getKbId());
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        chatMessageMapper.insert(message);
    }

    private String sessionTitle(String question) {
        String normalized = question == null || question.isBlank() ? "新的对话" : question.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private String sessionTitle(String currentTitle, String question) {
        if (currentTitle != null && !"新的对话".equals(currentTitle)) {
            return currentTitle;
        }
        return sessionTitle(question);
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getKbId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private void saveRecord(Long userId, Long sessionId, RagAskResponse response) {
        QaRecord record = new QaRecord();
        record.setUserId(userId);
        record.setKbId(response.kbId());
        record.setSessionId(sessionId);
        record.setQuestion(response.question());
        record.setAnswer(response.answer());
        record.setHitCount(response.hitCount());
        record.setCitationsJson(writeCitations(response.citations()));
        record.setAnswerStyle(response.answerStyle());
        record.setAnswerSource(response.answerSource());
        record.setModelName(response.modelName());
        record.setLatencyMs(response.latencyMs());
        record.setFallback(Boolean.TRUE.equals(response.fallback()));
        record.setCreatedAt(LocalDateTime.now());
        qaRecordMapper.insert(record);
        saveCitations(record, response.citations());
    }

    private void saveCitations(QaRecord record, List<RagCitationResponse> citations) {
        for (int index = 0; index < citations.size(); index++) {
            RagCitationResponse citation = citations.get(index);
            QaCitation entity = new QaCitation();
            entity.setQaRecordId(record.getId());
            entity.setUserId(record.getUserId());
            entity.setKbId(record.getKbId());
            entity.setChunkId(citation.chunkId());
            entity.setDocumentId(citation.documentId());
            entity.setDocumentName(citation.documentName());
            entity.setChunkNo(citation.chunkNo());
            entity.setRankNo(index + 1);
            entity.setContent(citation.content());
            entity.setScore(citation.score());
            entity.setVectorScore(citation.vectorScore());
            entity.setKeywordScore(citation.keywordScore());
            entity.setCreatedAt(record.getCreatedAt());
            qaCitationMapper.insert(entity);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private RagAskResponse withCacheHit(RagAskResponse response, Long sessionId, Long latencyMs) {
        return new RagAskResponse(
                response.kbId(),
                sessionId,
                response.question(),
                response.answer(),
                response.hitCount(),
                response.citations(),
                response.answerStyle(),
                response.answerSource(),
                response.modelName(),
                latencyMs,
                true,
                response.fallback()
        );
    }

    private QaRecordResponse toRecordResponse(QaRecord record) {
        return new QaRecordResponse(
                record.getId(),
                record.getKbId(),
                record.getSessionId(),
                record.getQuestion(),
                record.getAnswer(),
                record.getHitCount(),
                readCitations(record),
                record.getAnswerStyle(),
                record.getAnswerSource(),
                record.getModelName(),
                record.getLatencyMs(),
                record.getFallback(),
                record.getFeedbackScore(),
                record.getFeedbackNote(),
                record.getFeedbackAt(),
                record.getCreatedAt()
        );
    }

    private String exportCsv(List<QaRecordResponse> records) {
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append(String.join(",",
                "创建时间",
                "问题",
                "回答",
                "回答风格",
                "引用数",
                "回答来源",
                "模型",
                "耗时ms",
                "是否降级",
                "反馈",
                "引用来源"
        )).append("\n");
        for (QaRecordResponse record : records) {
            builder.append(csv(record.createdAt()))
                    .append(',').append(csv(record.question()))
                    .append(',').append(csv(record.answer()))
                    .append(',').append(csv(record.answerStyle()))
                    .append(',').append(csv(record.hitCount()))
                    .append(',').append(csv(record.answerSource()))
                    .append(',').append(csv(record.modelName()))
                    .append(',').append(csv(record.latencyMs()))
                    .append(',').append(csv(Boolean.TRUE.equals(record.fallback()) ? "是" : "否"))
                    .append(',').append(csv(feedbackText(record.feedbackScore())))
                    .append(',').append(csv(citationSummary(record.citations())))
                    .append("\n");
        }
        return builder.toString();
    }

    private String exportMarkdown(Long kbId, List<QaRecordResponse> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("# RAG 问答历史导出\n\n");
        builder.append("- 知识库 ID：").append(kbId).append("\n");
        builder.append("- 导出数量：").append(records.size()).append("\n\n");
        for (int index = 0; index < records.size(); index++) {
            QaRecordResponse record = records.get(index);
            builder.append("## ").append(index + 1).append(". ").append(escapeMarkdownTitle(record.question())).append("\n\n");
            builder.append("- 创建时间：").append(record.createdAt()).append("\n");
            builder.append("- 回答风格：").append(nullToDash(record.answerStyle())).append("\n");
            builder.append("- 回答来源：").append(nullToDash(record.answerSource())).append("\n");
            builder.append("- 模型：").append(nullToDash(record.modelName())).append("\n");
            builder.append("- 耗时：").append(record.latencyMs() == null ? "-" : record.latencyMs() + " ms").append("\n");
            builder.append("- 是否降级：").append(Boolean.TRUE.equals(record.fallback()) ? "是" : "否").append("\n");
            builder.append("- 反馈：").append(feedbackText(record.feedbackScore())).append("\n\n");
            builder.append("### 回答\n\n");
            builder.append(nullToDash(record.answer())).append("\n\n");
            builder.append("### 引用来源\n\n");
            if (record.citations().isEmpty()) {
                builder.append("无\n\n");
            } else {
                for (int citationIndex = 0; citationIndex < record.citations().size(); citationIndex++) {
                    RagCitationResponse citation = record.citations().get(citationIndex);
                    builder.append("#### [").append(citationIndex + 1).append("] ")
                            .append(citation.documentName())
                            .append(" · chunk ")
                            .append(citation.chunkNo())
                            .append(" · final ")
                            .append(citation.score())
                            .append("\n\n");
                    builder.append("> ").append(citation.content().replace("\n", "\n> ")).append("\n\n");
                }
            }
        }
        return builder.toString();
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String citationSummary(List<RagCitationResponse> citations) {
        return citations.stream()
                .map(citation -> "[%s] %s chunk %s final %s".formatted(
                        citation.chunkId(),
                        citation.documentName(),
                        citation.chunkNo(),
                        citation.score()))
                .collect(Collectors.joining(" | "));
    }

    private String feedbackText(Integer score) {
        if (score == null || score == 0) {
            return "未反馈";
        }
        return score > 0 ? "有帮助" : "没帮助";
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String escapeMarkdownTitle(String value) {
        return nullToDash(value).replace("\n", " ").replace("#", "\\#").trim();
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private String writeCitations(List<RagCitationResponse> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "问答引用保存失败");
        }
    }

    private List<RagCitationResponse> readCitations(QaRecord record) {
        List<QaCitation> citationRows = qaCitationMapper.selectList(new LambdaQueryWrapper<QaCitation>()
                .eq(QaCitation::getQaRecordId, record.getId())
                .orderByAsc(QaCitation::getRankNo));
        if (!citationRows.isEmpty()) {
            return citationRows.stream()
                    .map(row -> new RagCitationResponse(
                            row.getChunkId(),
                            row.getDocumentId(),
                            row.getDocumentName(),
                            row.getChunkNo(),
                            row.getContent(),
                            row.getScore(),
                            row.getVectorScore(),
                            row.getKeywordScore()
                    ))
                    .toList();
        }
        return readCitationsJson(record.getCitationsJson());
    }

    private List<RagCitationResponse> readCitationsJson(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationsJson, new TypeReference<List<RagCitationResponse>>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
