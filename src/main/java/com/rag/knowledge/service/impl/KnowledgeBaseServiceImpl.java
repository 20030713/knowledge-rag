package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.config.UploadProperties;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.entity.KnowledgeBaseMember;
import com.rag.knowledge.domain.entity.QaRecord;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.domain.enums.KnowledgeBaseMemberRole;
import com.rag.knowledge.domain.enums.KnowledgeBaseVisibility;
import com.rag.knowledge.dto.kb.KnowledgeBaseCreateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseBackupResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseImportResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberCandidateResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberUpdateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseUpdateRequest;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.DocumentChunkMapper;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.repository.KnowledgeBaseMemberMapper;
import com.rag.knowledge.repository.QaRecordMapper;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.rag.TextChunker;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.KnowledgeBaseService;
import com.rag.knowledge.service.RagAnswerCacheService;
import com.rag.knowledge.vector.VectorStoreService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final QaRecordMapper qaRecordMapper;
    private final UploadProperties uploadProperties;
    private final RagAnswerCacheService ragAnswerCacheService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeBasePermissionService permissionService;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseServiceImpl(
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeBaseMemberMapper memberMapper,
            UserMapper userMapper,
            DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            QaRecordMapper qaRecordMapper,
            UploadProperties uploadProperties,
            RagAnswerCacheService ragAnswerCacheService,
            VectorStoreService vectorStoreService,
            KnowledgeBasePermissionService permissionService,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.qaRecordMapper = qaRecordMapper;
        this.uploadProperties = uploadProperties;
        this.ragAnswerCacheService = ragAnswerCacheService;
        this.vectorStoreService = vectorStoreService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        ensureNameAvailable(loginUser.userId(), request.name(), null);

        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(loginUser.userId());
        knowledgeBase.setName(request.name());
        knowledgeBase.setDescription(request.description());
        knowledgeBase.setVisibility(KnowledgeBaseVisibility.PRIVATE.name());
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBaseMapper.insert(knowledgeBase);
        return toResponse(knowledgeBase, KnowledgeBaseMemberRole.OWNER);
    }

    @Override
    public List<KnowledgeBaseResponse> listMine() {
        LoginUser loginUser = UserContext.getRequired();
        List<KnowledgeBase> owned = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, loginUser.userId()));
        List<KnowledgeBaseMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseMember>()
                .eq(KnowledgeBaseMember::getUserId, loginUser.userId()));
        Set<Long> joinedIds = memberships.stream()
                .map(KnowledgeBaseMember::getKbId)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeBaseMemberRole> memberRoles = memberships.stream()
                .collect(Collectors.toMap(
                        KnowledgeBaseMember::getKbId,
                        member -> KnowledgeBaseMemberRole.from(member.getRole()),
                        (left, right) -> left
                ));
        List<KnowledgeBase> joined = joinedIds.isEmpty()
                ? List.of()
                : knowledgeBaseMapper.selectBatchIds(joinedIds);
        List<KnowledgeBase> accessible = new ArrayList<>();
        accessible.addAll(owned);
        accessible.addAll(joined.stream()
                .filter(item -> !item.getUserId().equals(loginUser.userId()))
                .toList());
        return accessible.stream()
                .sorted(Comparator.comparing(
                        KnowledgeBase::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .map(item -> toResponse(item, item.getUserId().equals(loginUser.userId())
                        ? KnowledgeBaseMemberRole.OWNER
                        : memberRoles.getOrDefault(item.getId(), KnowledgeBaseMemberRole.VIEWER)))
                .toList();
    }

    @Override
    public KnowledgeBaseResponse getMine(Long id) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(id);
        return toResponse(knowledgeBaseMapper.selectById(access.kbId()), access.role());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireAdmin(id);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(access.kbId());
        ensureNameAvailable(knowledgeBase.getUserId(), request.name(), id);

        knowledgeBase.setName(request.name());
        knowledgeBase.setDescription(request.description());
        applyChunkingSettings(knowledgeBase, request.chunkSize(), request.chunkOverlap(), request.minBreakSize());
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
        return toResponse(knowledgeBase, access.role());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireOwner(id);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(access.kbId());
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, knowledgeBase.getUserId())
                .eq(Document::getKbId, knowledgeBase.getId()));

        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, knowledgeBase.getUserId())
                .eq(DocumentChunk::getKbId, knowledgeBase.getId()));
        vectorStoreService.deleteKnowledgeBase(knowledgeBase.getUserId(), knowledgeBase.getId());
        qaRecordMapper.delete(new LambdaQueryWrapper<QaRecord>()
                .eq(QaRecord::getKbId, knowledgeBase.getId()));
        documentMapper.delete(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, knowledgeBase.getUserId())
                .eq(Document::getKbId, knowledgeBase.getId()));
        memberMapper.delete(new LambdaQueryWrapper<KnowledgeBaseMember>()
                .eq(KnowledgeBaseMember::getKbId, knowledgeBase.getId()));
        knowledgeBaseMapper.deleteById(knowledgeBase.getId());
        afterCommit(() -> {
            ragAnswerCacheService.evictKnowledgeBase(knowledgeBase.getId());
            documents.forEach(this::deleteStoredFile);
        });
    }

    @Override
    public List<KnowledgeBaseMemberResponse> listMembers(Long id) {
        permissionService.requireRead(id);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        List<KnowledgeBaseMember> members = memberMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseMember>()
                .eq(KnowledgeBaseMember::getKbId, id)
                .orderByDesc(KnowledgeBaseMember::getUpdatedAt));
        Map<Long, User> users = usersById(members.stream()
                .map(KnowledgeBaseMember::getUserId)
                .collect(Collectors.toSet()));
        User owner = userMapper.selectById(knowledgeBase.getUserId());
        List<KnowledgeBaseMemberResponse> responses = new ArrayList<>();
        responses.add(new KnowledgeBaseMemberResponse(
                null,
                knowledgeBase.getUserId(),
                owner == null ? "-" : owner.getUsername(),
                KnowledgeBaseMemberRole.OWNER.name(),
                true,
                knowledgeBase.getCreatedAt(),
                knowledgeBase.getUpdatedAt()
        ));
        responses.addAll(members.stream()
                .map(member -> toMemberResponse(member, users.get(member.getUserId())))
                .toList());
        return responses;
    }

    @Override
    public List<KnowledgeBaseMemberCandidateResponse> searchMemberCandidates(
            Long id,
            String keyword,
            Integer limit
    ) {
        permissionService.requireAdmin(id);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.length() < 2) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit == null ? 10 : limit, 20));
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        Set<Long> excludedUserIds = memberMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseMember>()
                        .eq(KnowledgeBaseMember::getKbId, id))
                .stream()
                .map(KnowledgeBaseMember::getUserId)
                .collect(Collectors.toSet());
        excludedUserIds.add(knowledgeBase.getUserId());

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getEnabled, true)
                .like(User::getUsername, safeKeyword)
                .notIn(User::getId, excludedUserIds)
                .orderByAsc(User::getUsername)
                .last("LIMIT " + safeLimit);
        return userMapper.selectList(wrapper).stream()
                .map(user -> new KnowledgeBaseMemberCandidateResponse(user.getId(), user.getUsername()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseMemberResponse addMember(Long id, KnowledgeBaseMemberRequest request) {
        permissionService.requireAdmin(id);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username().trim())
                .eq(User::getEnabled, true)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到已启用的注册用户");
        }
        if (knowledgeBase.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识库拥有者已经具备完整权限");
        }
        KnowledgeBaseMemberRole role = memberRole(request.role());
        KnowledgeBaseMember member = memberMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseMember>()
                .eq(KnowledgeBaseMember::getKbId, id)
                .eq(KnowledgeBaseMember::getUserId, user.getId())
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (member == null) {
            member = new KnowledgeBaseMember();
            member.setKbId(id);
            member.setUserId(user.getId());
            member.setRole(role.name());
            member.setCreatedAt(now);
            member.setUpdatedAt(now);
            memberMapper.insert(member);
        } else {
            member.setRole(role.name());
            member.setUpdatedAt(now);
            memberMapper.updateById(member);
        }
        touchKnowledgeBase(knowledgeBase);
        return toMemberResponse(member, user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseMemberResponse updateMember(Long id, Long memberId, KnowledgeBaseMemberUpdateRequest request) {
        permissionService.requireAdmin(id);
        KnowledgeBaseMember member = getMember(id, memberId);
        KnowledgeBaseMemberRole role = memberRole(request.role());
        member.setRole(role.name());
        member.setUpdatedAt(LocalDateTime.now());
        memberMapper.updateById(member);
        touchKnowledgeBase(knowledgeBaseMapper.selectById(id));
        return toMemberResponse(member, userMapper.selectById(member.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long id, Long memberId) {
        permissionService.requireAdmin(id);
        KnowledgeBaseMember member = getMember(id, memberId);
        memberMapper.deleteById(member.getId());
        touchKnowledgeBase(knowledgeBaseMapper.selectById(id));
    }

    @Override
    public KnowledgeBaseBackupResponse exportBackup(Long id) {
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireAdmin(id);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(access.kbId());
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, knowledgeBase.getId())
                .orderByAsc(Document::getCreatedAt));
        List<KnowledgeBaseBackupResponse.DocumentItem> documentItems = documents.stream()
                .map(this::toBackupDocument)
                .toList();
        List<KnowledgeBaseBackupResponse.QaRecordItem> qaRecords = qaRecordMapper.selectList(new LambdaQueryWrapper<QaRecord>()
                        .eq(QaRecord::getKbId, knowledgeBase.getId())
                        .orderByAsc(QaRecord::getCreatedAt))
                .stream()
                .map(record -> new KnowledgeBaseBackupResponse.QaRecordItem(
                        record.getQuestion(),
                        record.getAnswer(),
                        record.getHitCount(),
                        record.getCitationsJson(),
                        record.getAnswerStyle(),
                        record.getAnswerSource(),
                        record.getModelName(),
                        record.getLatencyMs(),
                        record.getFallback(),
                        record.getFeedbackScore(),
                        record.getFeedbackNote(),
                        record.getFeedbackAt(),
                        record.getCreatedAt()
                ))
                .toList();
        return new KnowledgeBaseBackupResponse(
                "knowledge-rag-kb-backup",
                2,
                LocalDateTime.now(),
                new KnowledgeBaseBackupResponse.KnowledgeBaseItem(
                        knowledgeBase.getName(),
                        knowledgeBase.getDescription(),
                        knowledgeBase.getVisibility(),
                        effectiveChunkSize(knowledgeBase),
                        effectiveChunkOverlap(knowledgeBase),
                        effectiveMinBreakSize(knowledgeBase),
                        knowledgeBase.getCreatedAt(),
                        knowledgeBase.getUpdatedAt()
                ),
                documentItems,
                qaRecords
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseImportResponse importBackup(String jsonContent) {
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBaseBackupResponse backup;
        try {
            backup = objectMapper.readValue(jsonContent, KnowledgeBaseBackupResponse.class);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid backup json");
        }
        if (backup == null
                || !"knowledge-rag-kb-backup".equals(backup.format())
                || backup.knowledgeBase() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unsupported backup format");
        }

        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(loginUser.userId());
        knowledgeBase.setName(importName(loginUser.userId(), backup.knowledgeBase().name()));
        knowledgeBase.setDescription(backup.knowledgeBase().description());
        knowledgeBase.setVisibility(KnowledgeBaseVisibility.PRIVATE.name());
        applyChunkingSettings(
                knowledgeBase,
                backup.knowledgeBase().chunkSize(),
                backup.knowledgeBase().chunkOverlap(),
                backup.knowledgeBase().minBreakSize()
        );
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBaseMapper.insert(knowledgeBase);

        int documentCount = 0;
        int chunkCount = 0;
        Map<Long, Long> documentIdMap = new HashMap<>();
        for (KnowledgeBaseBackupResponse.DocumentItem item : nullToEmpty(backup.documents())) {
            Document document = new Document();
            document.setUserId(loginUser.userId());
            document.setKbId(knowledgeBase.getId());
            document.setFileName(limit(item.fileName(), 255, "imported-document.txt"));
            document.setFileType(limit(item.fileType(), 32, "txt"));
            document.setFileUrl("backup/" + knowledgeBase.getId() + "/" + sanitizePathPart(item.fileName()));
            document.setFileSize(item.fileSize() == null ? 0L : item.fileSize());
            document.setStatus(item.status() == null ? "COMPLETED" : item.status());
            document.setErrorMsg(item.errorMsg());
            document.setRetryCount(item.retryCount() == null ? 0 : item.retryCount());
            document.setParseDurationMs(item.parseDurationMs());
            document.setCreatedAt(now);
            document.setUpdatedAt(now);
            documentMapper.insert(document);
            documentCount++;
            if (item.originalId() != null) {
                documentIdMap.put(item.originalId(), document.getId());
            }
            int importedChunks = importChunks(loginUser.userId(), knowledgeBase.getId(), document.getId(), item.chunks(), now);
            chunkCount += importedChunks;
            document.setChunkCount(importedChunks);
            documentMapper.updateById(document);
        }

        int qaRecordCount = 0;
        for (KnowledgeBaseBackupResponse.QaRecordItem item : nullToEmpty(backup.qaRecords())) {
            QaRecord record = new QaRecord();
            record.setUserId(loginUser.userId());
            record.setKbId(knowledgeBase.getId());
            record.setQuestion(item.question());
            record.setAnswer(item.answer());
            record.setHitCount(item.hitCount() == null ? 0 : item.hitCount());
            record.setCitationsJson(remapCitations(item.citationsJson(), documentIdMap));
            record.setAnswerStyle(item.answerStyle() == null ? "STRICT" : item.answerStyle());
            record.setAnswerSource(item.answerSource() == null ? "IMPORTED" : item.answerSource());
            record.setModelName(item.modelName());
            record.setLatencyMs(item.latencyMs());
            record.setFallback(item.fallback() == null ? false : item.fallback());
            record.setFeedbackScore(item.feedbackScore());
            record.setFeedbackNote(item.feedbackNote());
            record.setFeedbackAt(item.feedbackAt());
            record.setCreatedAt(item.createdAt() == null ? now : item.createdAt());
            qaRecordMapper.insert(record);
            qaRecordCount++;
        }

        return new KnowledgeBaseImportResponse(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                documentCount,
                chunkCount,
                qaRecordCount,
                "Import completed. Rebuild or sync vector index if pgvector is enabled."
        );
    }

    private KnowledgeBaseBackupResponse.DocumentItem toBackupDocument(Document document) {
        List<KnowledgeBaseBackupResponse.ChunkItem> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, document.getId())
                        .orderByAsc(DocumentChunk::getChunkNo))
                .stream()
                .map(chunk -> new KnowledgeBaseBackupResponse.ChunkItem(
                        chunk.getChunkNo(),
                        chunk.getContent(),
                        chunk.getCharCount(),
                        chunk.getEmbeddingModel(),
                        chunk.getEmbeddingJson(),
                        chunk.getCreatedAt()
                ))
                .toList();
        return new KnowledgeBaseBackupResponse.DocumentItem(
                document.getId(),
                document.getFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getStatus(),
                document.getErrorMsg(),
                document.getRetryCount(),
                document.getChunkCount(),
                document.getParseDurationMs(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                chunks
        );
    }

    private int importChunks(
            Long userId,
            Long kbId,
            Long documentId,
            List<KnowledgeBaseBackupResponse.ChunkItem> chunks,
            LocalDateTime now
    ) {
        int count = 0;
        for (KnowledgeBaseBackupResponse.ChunkItem item : nullToEmpty(chunks)) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setUserId(userId);
            chunk.setKbId(kbId);
            chunk.setDocumentId(documentId);
            chunk.setChunkNo(item.chunkNo() == null ? count + 1 : item.chunkNo());
            chunk.setContent(item.content() == null ? "" : item.content());
            chunk.setCharCount(item.charCount() == null ? chunk.getContent().length() : item.charCount());
            chunk.setVectorId(null);
            chunk.setEmbeddingModel(item.embeddingModel());
            chunk.setEmbeddingJson(item.embeddingJson());
            chunk.setCreatedAt(item.createdAt() == null ? now : item.createdAt());
            documentChunkMapper.insert(chunk);
            count++;
        }
        return count;
    }

    private String importName(Long userId, String originalName) {
        String baseName = originalName == null || originalName.isBlank() ? "Imported knowledge base" : originalName.trim();
        String suffix = " imported " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String candidate = limit(baseName + " (" + suffix + ")", 128, "Imported knowledge base");
        int index = 1;
        while (knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getName, candidate)) > 0) {
            candidate = limit(baseName + " (" + suffix + "-" + index + ")", 128, "Imported knowledge base");
            index++;
        }
        return candidate;
    }

    private String remapCitations(String citationsJson, Map<Long, Long> documentIdMap) {
        if (citationsJson == null || citationsJson.isBlank() || documentIdMap.isEmpty()) {
            return citationsJson;
        }
        String remapped = citationsJson;
        for (Map.Entry<Long, Long> entry : documentIdMap.entrySet()) {
            remapped = remapped.replace("\"documentId\":" + entry.getKey(), "\"documentId\":" + entry.getValue());
            remapped = remapped.replace("\"documentId\":\"" + entry.getKey() + "\"", "\"documentId\":\"" + entry.getValue() + "\"");
        }
        return remapped;
    }

    private <T> List<T> nullToEmpty(List<T> items) {
        return items == null ? List.of() : items;
    }

    private String limit(String value, int maxLength, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private String sanitizePathPart(String value) {
        String safe = value == null || value.isBlank() ? "document" : value.trim();
        safe = safe.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        return safe.length() <= 120 ? safe : safe.substring(0, 120);
    }

    private KnowledgeBaseMember getMember(Long kbId, Long memberId) {
        KnowledgeBaseMember member = memberMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseMember>()
                .eq(KnowledgeBaseMember::getKbId, kbId)
                .eq(KnowledgeBaseMember::getId, memberId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库成员不存在");
        }
        return member;
    }

    private KnowledgeBaseMemberRole memberRole(String value) {
        KnowledgeBaseMemberRole role;
        try {
            role = KnowledgeBaseMemberRole.from(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员角色必须为管理员、编辑者或只读成员");
        }
        if (role == KnowledgeBaseMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能将普通成员设置为知识库拥有者");
        }
        return role;
    }

    private void ensureNameAvailable(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getName, name);
        if (excludeId != null) {
            wrapper.ne(KnowledgeBase::getId, excludeId);
        }
        if (knowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledge base name already exists");
        }
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase, KnowledgeBaseMemberRole role) {
        User owner = userMapper.selectById(knowledgeBase.getUserId());
        int memberCount = Math.toIntExact(memberMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseMember>()
                .eq(KnowledgeBaseMember::getKbId, knowledgeBase.getId())));
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getUserId(),
                owner == null ? "-" : owner.getUsername(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getVisibility(),
                role.name(),
                role == KnowledgeBaseMemberRole.OWNER,
                memberCount,
                effectiveChunkSize(knowledgeBase),
                effectiveChunkOverlap(knowledgeBase),
                effectiveMinBreakSize(knowledgeBase),
                knowledgeBase.getCreatedAt(),
                knowledgeBase.getUpdatedAt()
        );
    }

    private void applyChunkingSettings(
            KnowledgeBase knowledgeBase,
            Integer requestedChunkSize,
            Integer requestedChunkOverlap,
            Integer requestedMinBreakSize
    ) {
        int chunkSize = requestedChunkSize == null ? effectiveChunkSize(knowledgeBase) : requestedChunkSize;
        int chunkOverlap = requestedChunkOverlap == null ? effectiveChunkOverlap(knowledgeBase) : requestedChunkOverlap;
        int minBreakSize = requestedMinBreakSize == null ? effectiveMinBreakSize(knowledgeBase) : requestedMinBreakSize;
        if (chunkSize < 200 || chunkSize > 1500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "切片长度必须在 200 到 1500 之间");
        }
        if (chunkOverlap < 0 || chunkOverlap > 300 || chunkOverlap >= chunkSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "重叠长度必须小于切片长度，且不能超过 300");
        }
        if (chunkOverlap > Math.floor(chunkSize * 0.3)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "重叠长度不能超过切片长度的 30%");
        }
        if (minBreakSize < 50 || minBreakSize > 1200 || minBreakSize > chunkSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "最小切分位置必须在 50 和切片长度之间");
        }
        knowledgeBase.setChunkSize(chunkSize);
        knowledgeBase.setChunkOverlap(chunkOverlap);
        knowledgeBase.setMinBreakSize(minBreakSize);
    }

    private int effectiveChunkSize(KnowledgeBase knowledgeBase) {
        return knowledgeBase.getChunkSize() == null ? TextChunker.DEFAULT_CHUNK_SIZE : knowledgeBase.getChunkSize();
    }

    private int effectiveChunkOverlap(KnowledgeBase knowledgeBase) {
        return knowledgeBase.getChunkOverlap() == null ? TextChunker.DEFAULT_OVERLAP_SIZE : knowledgeBase.getChunkOverlap();
    }

    private int effectiveMinBreakSize(KnowledgeBase knowledgeBase) {
        return knowledgeBase.getMinBreakSize() == null ? TextChunker.DEFAULT_MIN_BREAK_SIZE : knowledgeBase.getMinBreakSize();
    }

    private KnowledgeBaseMemberResponse toMemberResponse(KnowledgeBaseMember member, User user) {
        return new KnowledgeBaseMemberResponse(
                member.getId(),
                member.getUserId(),
                user == null ? "-" : user.getUsername(),
                KnowledgeBaseMemberRole.from(member.getRole()).name(),
                false,
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    private Map<Long, User> usersById(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, HashMap::new));
    }

    private void touchKnowledgeBase(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            return;
        }
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
    }

    private void deleteStoredFile(Document document) {
        if (document.getFileUrl() == null || document.getFileUrl().isBlank()) {
            return;
        }
        Path path = Path.of(uploadProperties.getRootPath()).resolve(document.getFileUrl()).normalize();
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete stored file for knowledge base cleanup path={}", path, exception);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
