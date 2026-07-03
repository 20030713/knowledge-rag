package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.config.UploadProperties;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.entity.KnowledgeBaseMember;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.repository.DocumentChunkMapper;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.repository.KnowledgeBaseMemberMapper;
import com.rag.knowledge.repository.QaRecordMapper;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.RagAnswerCacheService;
import com.rag.knowledge.vector.VectorStoreService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplMemberTests {

    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private KnowledgeBaseMemberMapper memberMapper;
    @Mock private UserMapper userMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentChunkMapper documentChunkMapper;
    @Mock private QaRecordMapper qaRecordMapper;
    @Mock private UploadProperties uploadProperties;
    @Mock private RagAnswerCacheService ragAnswerCacheService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private KnowledgeBasePermissionService permissionService;

    @Test
    void searchesOnlyEligibleMemberCandidates() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(10L);
        knowledgeBase.setUserId(1L);
        KnowledgeBaseMember existingMember = new KnowledgeBaseMember();
        existingMember.setKbId(10L);
        existingMember.setUserId(2L);
        User candidate = new User();
        candidate.setId(3L);
        candidate.setUsername("alice_tech");
        candidate.setEnabled(true);

        when(knowledgeBaseMapper.selectById(10L)).thenReturn(knowledgeBase);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existingMember));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(candidate));

        var result = service().searchMemberCandidates(10L, "alice", 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().userId()).isEqualTo(3L);
        assertThat(result.getFirst().username()).isEqualTo("alice_tech");
        verify(permissionService).requireAdmin(10L);
    }

    @Test
    void skipsUserLookupForShortCandidateKeyword() {
        var result = service().searchMemberCandidates(10L, "a", 10);

        assertThat(result).isEmpty();
        verify(permissionService).requireAdmin(10L);
        verify(userMapper, never()).selectList(any(Wrapper.class));
    }

    private KnowledgeBaseServiceImpl service() {
        return new KnowledgeBaseServiceImpl(
                knowledgeBaseMapper,
                memberMapper,
                userMapper,
                documentMapper,
                documentChunkMapper,
                qaRecordMapper,
                uploadProperties,
                ragAnswerCacheService,
                vectorStoreService,
                permissionService,
                new ObjectMapper()
        );
    }
}
