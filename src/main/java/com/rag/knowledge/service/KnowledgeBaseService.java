package com.rag.knowledge.service;

import com.rag.knowledge.dto.kb.KnowledgeBaseCreateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseBackupResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseImportResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberCandidateResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberUpdateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseUpdateRequest;
import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request);

    List<KnowledgeBaseResponse> listMine();

    KnowledgeBaseResponse getMine(Long id);

    KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request);

    void delete(Long id);

    List<KnowledgeBaseMemberResponse> listMembers(Long id);

    List<KnowledgeBaseMemberCandidateResponse> searchMemberCandidates(Long id, String keyword, Integer limit);

    KnowledgeBaseMemberResponse addMember(Long id, KnowledgeBaseMemberRequest request);

    KnowledgeBaseMemberResponse updateMember(Long id, Long memberId, KnowledgeBaseMemberUpdateRequest request);

    void removeMember(Long id, Long memberId);

    KnowledgeBaseBackupResponse exportBackup(Long id);

    KnowledgeBaseImportResponse importBackup(String jsonContent);
}
