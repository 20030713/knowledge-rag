package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.config.UploadProperties;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.domain.enums.KnowledgeBaseMemberRole;
import com.rag.knowledge.job.DocumentParseTask;
import com.rag.knowledge.repository.DocumentChunkMapper;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.service.DistributedLockService;
import com.rag.knowledge.service.DocumentParseProgressService;
import com.rag.knowledge.service.DocumentTaskQueueService;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.RagAnswerCacheService;
import com.rag.knowledge.vector.TextEmbeddingService;
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
class DocumentServiceImplVectorSyncTests {

    private static final String CURRENT_MODEL = "openai-compatible-embedding:dashscope:text-embedding-v3:2";

    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentChunkMapper documentChunkMapper;
    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private UploadProperties uploadProperties;
    @Mock private RagAnswerCacheService ragAnswerCacheService;
    @Mock private TextEmbeddingService textEmbeddingService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private KnowledgeBasePermissionService permissionService;
    @Mock private DistributedLockService distributedLockService;
    @Mock private DocumentParseTask documentParseTask;
    @Mock private DocumentParseProgressService parseProgressService;
    @Mock private DocumentTaskQueueService taskQueueService;

    @Test
    void reembedsChunksCreatedByPreviousModel() throws Exception {
        DocumentChunk chunk = chunk("old-v4-model", "[0.1,0.2]");
        prepareSync(chunk);
        when(textEmbeddingService.modelName()).thenReturn("text-embedding-v3");
        when(textEmbeddingService.embed("Java Redis cache")).thenReturn(new double[]{0.6, 0.8});

        var response = service().syncKnowledgeBaseVectors(10L);

        assertThat(response.syncedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isZero();
        assertThat(chunk.getEmbeddingModel()).isEqualTo(CURRENT_MODEL);
        assertThat(chunk.getVectorId()).isEqualTo("text-embedding-v3");
        assertThat(new ObjectMapper().readValue(chunk.getEmbeddingJson(), double[].class))
                .containsExactly(0.6, 0.8);
        verify(textEmbeddingService).embed("Java Redis cache");
        verify(documentChunkMapper).updateById(chunk);
        verify(vectorStoreService).upsert(chunk, new double[]{0.6, 0.8});
        verify(ragAnswerCacheService).evictKnowledgeBase(10L);
    }

    @Test
    void reusesEmbeddingAlreadyCreatedByCurrentModel() {
        DocumentChunk chunk = chunk(CURRENT_MODEL, "[0.6,0.8]");
        prepareSync(chunk);

        var response = service().syncKnowledgeBaseVectors(10L);

        assertThat(response.syncedCount()).isEqualTo(1);
        verify(textEmbeddingService, never()).embed(any());
        verify(documentChunkMapper, never()).updateById(any(DocumentChunk.class));
        verify(vectorStoreService).upsert(chunk, new double[]{0.6, 0.8});
    }

    private void prepareSync(DocumentChunk chunk) {
        when(permissionService.requireEdit(10L)).thenReturn(new KnowledgeBasePermissionService.KnowledgeBaseAccess(
                10L, 1L, 1L, KnowledgeBaseMemberRole.OWNER, true
        ));
        when(documentChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk));
        when(vectorStoreService.available()).thenReturn(true);
        when(vectorStoreService.backendName()).thenReturn("pgvector");
        when(textEmbeddingService.modelNamespace()).thenReturn(CURRENT_MODEL);
        when(textEmbeddingService.dimensions()).thenReturn(2);
    }

    private DocumentChunk chunk(String embeddingModel, String embeddingJson) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(100L);
        chunk.setUserId(1L);
        chunk.setKbId(10L);
        chunk.setDocumentId(20L);
        chunk.setChunkNo(0);
        chunk.setContent("Java Redis cache");
        chunk.setEmbeddingModel(embeddingModel);
        chunk.setEmbeddingJson(embeddingJson);
        return chunk;
    }

    private DocumentServiceImpl service() {
        return new DocumentServiceImpl(
                documentMapper,
                documentChunkMapper,
                knowledgeBaseMapper,
                uploadProperties,
                ragAnswerCacheService,
                textEmbeddingService,
                vectorStoreService,
                permissionService,
                distributedLockService,
                documentParseTask,
                new ObjectMapper(),
                parseProgressService,
                taskQueueService
        );
    }
}
