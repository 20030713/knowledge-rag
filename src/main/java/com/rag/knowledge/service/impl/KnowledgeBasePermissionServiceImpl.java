package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.entity.KnowledgeBaseMember;
import com.rag.knowledge.domain.enums.KnowledgeBaseMemberRole;
import com.rag.knowledge.domain.enums.KnowledgeBaseVisibility;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.repository.KnowledgeBaseMemberMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBasePermissionServiceImpl implements KnowledgeBasePermissionService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseMemberMapper memberMapper;
    private final DocumentMapper documentMapper;

    public KnowledgeBasePermissionServiceImpl(
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeBaseMemberMapper memberMapper,
            DocumentMapper documentMapper
    ) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.memberMapper = memberMapper;
        this.documentMapper = documentMapper;
    }

    @Override
    public KnowledgeBaseAccess requireRead(Long kbId) {
        return require(kbId, KnowledgeBaseMemberRole.VIEWER);
    }

    @Override
    public KnowledgeBaseAccess requireEdit(Long kbId) {
        return require(kbId, KnowledgeBaseMemberRole.EDITOR);
    }

    @Override
    public KnowledgeBaseAccess requireAdmin(Long kbId) {
        return require(kbId, KnowledgeBaseMemberRole.ADMIN);
    }

    @Override
    public KnowledgeBaseAccess requireOwner(Long kbId) {
        return require(kbId, KnowledgeBaseMemberRole.OWNER);
    }

    @Override
    public KnowledgeBaseAccess requireDocumentRead(Long documentId) {
        Document document = loadDocument(documentId);
        return requireRead(document.getKbId());
    }

    @Override
    public KnowledgeBaseAccess requireDocumentEdit(Long documentId) {
        Document document = loadDocument(documentId);
        return requireEdit(document.getKbId());
    }

    private KnowledgeBaseAccess require(Long kbId, KnowledgeBaseMemberRole required) {
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(kbId);
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
        KnowledgeBaseMemberRole role = roleOf(knowledgeBase, loginUser.userId());
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "knowledge base not found");
        }
        if (!role.atLeast(required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "permission denied");
        }
        return new KnowledgeBaseAccess(
                knowledgeBase.getId(),
                knowledgeBase.getUserId(),
                loginUser.userId(),
                role,
                knowledgeBase.getUserId().equals(loginUser.userId())
        );
    }

    private KnowledgeBaseMemberRole roleOf(KnowledgeBase knowledgeBase, Long userId) {
        if (knowledgeBase.getUserId().equals(userId)) {
            return KnowledgeBaseMemberRole.OWNER;
        }
        KnowledgeBaseMember member = memberMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseMember>()
                .eq(KnowledgeBaseMember::getKbId, knowledgeBase.getId())
                .eq(KnowledgeBaseMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member != null) {
            return KnowledgeBaseMemberRole.from(member.getRole());
        }
        if (KnowledgeBaseVisibility.PUBLIC.name().equalsIgnoreCase(knowledgeBase.getVisibility())) {
            return KnowledgeBaseMemberRole.VIEWER;
        }
        return null;
    }

    private Document loadDocument(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "document not found");
        }
        return document;
    }
}
