package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.config.UploadProperties;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.entity.KnowledgeBaseMember;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
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
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void listsKnowledgeBasesSharedWithCurrentUser() {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase shared = new KnowledgeBase();
        shared.setId(10L);
        shared.setUserId(1L);
        shared.setName("Shared knowledge base");
        shared.setVisibility("PRIVATE");
        shared.setCreatedAt(now);
        shared.setUpdatedAt(now);

        KnowledgeBaseMember membership = new KnowledgeBaseMember();
        membership.setKbId(10L);
        membership.setUserId(2L);
        membership.setRole("EDITOR");

        User owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        UserContext.set(new LoginUser(2L, "member", "USER"));
        when(knowledgeBaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(membership));
        when(knowledgeBaseMapper.selectBatchIds(anyCollection())).thenReturn(List.of(shared));
        when(userMapper.selectById(1L)).thenReturn(owner);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        var result = service().listMine();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(10L);
        assertThat(result.getFirst().accessRole()).isEqualTo("EDITOR");
        assertThat(result.getFirst().owned()).isFalse();
        assertThat(result.getFirst().ownerUsername()).isEqualTo("owner");
    }

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
