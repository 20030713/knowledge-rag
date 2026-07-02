package com.rag.knowledge.service;

import com.rag.knowledge.domain.enums.KnowledgeBaseMemberRole;

public interface KnowledgeBasePermissionService {

    KnowledgeBaseAccess requireRead(Long kbId);

    KnowledgeBaseAccess requireEdit(Long kbId);

    KnowledgeBaseAccess requireAdmin(Long kbId);

    KnowledgeBaseAccess requireOwner(Long kbId);

    KnowledgeBaseAccess requireDocumentRead(Long documentId);

    KnowledgeBaseAccess requireDocumentEdit(Long documentId);

    record KnowledgeBaseAccess(
            Long kbId,
            Long ownerUserId,
            Long actorUserId,
            KnowledgeBaseMemberRole role,
            boolean owner
    ) {
        public boolean canEdit() {
            return role.atLeast(KnowledgeBaseMemberRole.EDITOR);
        }

        public boolean canAdmin() {
            return role.atLeast(KnowledgeBaseMemberRole.ADMIN);
        }
    }
}
